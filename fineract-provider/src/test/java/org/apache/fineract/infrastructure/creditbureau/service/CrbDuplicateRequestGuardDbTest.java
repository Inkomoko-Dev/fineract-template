/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.infrastructure.creditbureau.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.FileSystemResourceAccessor;
import org.apache.fineract.infrastructure.creditbureau.data.CrbRequestType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "CRB_GUARD_TEST_JDBC_URL", matches = ".+")
class CrbDuplicateRequestGuardDbTest {

    private static final long BUREAU_LATENCY_MS = 1500;

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private TransactionTemplate commandTransaction;
    private MockEnvironment env;
    private CrbDuplicateRequestGuard guard;
    private AtomicInteger bureauCalls;
    private ExecutorService pool;

    @BeforeEach
    void freshSchema() throws Exception {
        dataSource = new DriverManagerDataSource(System.getenv("CRB_GUARD_TEST_JDBC_URL"),
                System.getenv().getOrDefault("CRB_GUARD_TEST_JDBC_USER", "root"),
                System.getenv().getOrDefault("CRB_GUARD_TEST_JDBC_PASSWORD", ""));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class)) {
            jdbc.execute("DROP TABLE IF EXISTS `" + table + "`");
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        jdbc.execute("CREATE TABLE m_loan (id BIGINT AUTO_INCREMENT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE crb_bureau_report (id BIGINT AUTO_INCREMENT PRIMARY KEY, loan_id BIGINT NOT NULL)");
        jdbc.update("INSERT INTO m_loan (id) VALUES (1), (2)");

        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            FileSystemResourceAccessor files = new FileSystemResourceAccessor(
                    new File("src/main/resources/db/changelog/tenant/custom-changelog"));
            new Liquibase("CGLT-647_crb_request_guard.xml", files, database).update("");
        }

        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        commandTransaction = new TransactionTemplate(transactionManager);
        env = new MockEnvironment();
        guard = new CrbDuplicateRequestGuard(jdbc, transactionManager, env);
        bureauCalls = new AtomicInteger();
        pool = Executors.newFixedThreadPool(8);
    }

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    private Long verify(CrbRequestType type, long loanId) {
        return commandTransaction.execute(status -> {
            jdbc.queryForObject("SELECT COUNT(*) FROM crb_bureau_report", Integer.class);
            return guard.acquire(type, loanId).orElseGet(() -> callBureau(type, loanId));
        });
    }

    private Long callBureau(CrbRequestType type, long loanId) {
        bureauCalls.incrementAndGet();
        sleep(BUREAU_LATENCY_MS);
        jdbc.update("INSERT INTO crb_bureau_report (loan_id) VALUES (?)", loanId);
        Long reportId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        guard.complete(type, loanId, reportId);
        return reportId;
    }

    private List<Long> clickAtOnce(int clicks, CrbRequestType type, long loanId) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> results = new ArrayList<>();
        for (int i = 0; i < clicks; i++) {
            Callable<Long> click = () -> {
                start.await();
                return verify(type, loanId);
            };
            results.add(pool.submit(click));
        }
        start.countDown();
        List<Long> reportIds = new ArrayList<>();
        for (Future<Long> result : results) {
            reportIds.add(result.get(60, TimeUnit.SECONDS));
        }
        return reportIds;
    }

    @Test
    void simultaneousClicksOnTheSameLoanMakeOneBureauCall() throws Exception {
        List<Long> reportIds = clickAtOnce(5, CrbRequestType.TRANSUNION_RWANDA, 1L);

        assertEquals(1, bureauCalls.get());
        assertEquals(1, new HashSet<>(reportIds).size());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM crb_bureau_report", Integer.class));
    }

    @Test
    void everyMetropolReportIsGuarded() throws Exception {
        for (CrbRequestType type : List.of(CrbRequestType.METROPOL_IDENTITY, CrbRequestType.METROPOL_CREDIT_INFO_ENHANCED,
                CrbRequestType.METROPOL_REPORT_JSON)) {
            clickAtOnce(3, type, 1L);
        }

        assertEquals(3, bureauCalls.get());
    }

    @Test
    void aClickAfterTheWindowCallsTheBureauAgain() {
        env.setProperty(CrbDuplicateRequestGuard.WINDOW_SECONDS_PROPERTY, "1");

        Long first = verify(CrbRequestType.METROPOL_REPORT_JSON, 1L);
        sleep(2500);
        Long second = verify(CrbRequestType.METROPOL_REPORT_JSON, 1L);

        assertEquals(2, bureauCalls.get());
        assertNotEquals(first, second);
    }

    @Test
    void aClickWithinTheWindowReusesTheReport() {
        Long first = verify(CrbRequestType.METROPOL_IDENTITY, 1L);
        Long second = verify(CrbRequestType.METROPOL_IDENTITY, 1L);

        assertEquals(1, bureauCalls.get());
        assertEquals(first, second);
    }

    @Test
    void aFailedBureauCallDoesNotBlockTheRetry() {
        assertThrows(IllegalStateException.class, () -> commandTransaction.execute(status -> {
            guard.acquire(CrbRequestType.TRANSUNION_RWANDA, 1L);
            bureauCalls.incrementAndGet();
            throw new IllegalStateException("bureau unavailable");
        }));

        verify(CrbRequestType.TRANSUNION_RWANDA, 1L);

        assertEquals(2, bureauCalls.get());
    }

    @Test
    void differentLoansAreNotHeldUpByEachOther() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        long began = System.currentTimeMillis();
        Future<Long> first = pool.submit(() -> {
            start.await();
            return verify(CrbRequestType.TRANSUNION_RWANDA, 1L);
        });
        Future<Long> second = pool.submit(() -> {
            start.await();
            return verify(CrbRequestType.TRANSUNION_RWANDA, 2L);
        });
        start.countDown();
        first.get(60, TimeUnit.SECONDS);
        second.get(60, TimeUnit.SECONDS);

        assertEquals(2, bureauCalls.get());
        assertTrue(System.currentTimeMillis() - began < 2 * BUREAU_LATENCY_MS);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

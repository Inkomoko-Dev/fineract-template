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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OriginalScheduleExtractorSqlTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void createSchemaAndFixtures() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        jdbc.execute("CREATE TABLE m_loan (id BIGINT PRIMARY KEY, grace_on_arrears_ageing INT)");
        jdbc.execute("CREATE TABLE m_loan_repayment_schedule_history (id BIGINT AUTO_INCREMENT PRIMARY KEY, loan_id BIGINT,"
                + " loan_reschedule_request_id BIGINT, version INT, superseded_on_date DATE, duedate DATE, principal_amount DECIMAL(19,6),"
                + " interest_amount DECIMAL(19,6), fee_charges_amount DECIMAL(19,6), penalty_charges_amount DECIMAL(19,6))");
        for (long loanId = 1; loanId <= 3; loanId++) {
            jdbc.update("INSERT INTO m_loan VALUES (?, 0)", loanId);
            archive(loanId, null, 1, null, "1200");
        }
        archive(1, null, 2, "2026-04-10", "1000");
        archive(2, 5L, 2, "2026-04-10", "900");
    }

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 4, 30))));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    void archiveOfALiveScheduleReplacedOnADateDoesNotBecomeTheOriginalSchedule() {
        assertPrincipal(originalSchedule().get(1L), "1200");
    }

    @Test
    void archiveOfAnApprovedRescheduleStillBecomesTheOriginalSchedule() {
        assertPrincipal(originalSchedule().get(2L), "900");
    }

    @Test
    void loanWithOnlyItsOriginalScheduleIsAgedOnIt() {
        assertPrincipal(originalSchedule().get(3L), "1200");
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, List<LoanSchedulePeriodData>> originalSchedule() {
        try {
            final DatabaseTypeResolver resolver = mock(DatabaseTypeResolver.class);
            when(resolver.isMySQL()).thenReturn(true);
            final Class<?> type = Class.forName(LoanArrearsAgingServiceImpl.class.getName() + "$OriginalScheduleExtractor");
            final Constructor<?> constructor = type.getDeclaredConstructor(String.class, DatabaseSpecificSQLGenerator.class);
            constructor.setAccessible(true);
            final Object extractor = constructor.newInstance("1, 2, 3", new DatabaseSpecificSQLGenerator(resolver));
            final String sql = (String) ReflectionTestUtils.getField(extractor, "schema");
            return jdbc.query(sql, (ResultSetExtractor<Map<Long, List<LoanSchedulePeriodData>>>) extractor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertPrincipal(final List<LoanSchedulePeriodData> periods, final String principal) {
        assertEquals(3, periods.size());
        periods.forEach(period -> assertEquals(0, new BigDecimal(principal).compareTo(period.principalDue())));
    }

    private static void archive(final long loanId, final Long requestId, final int version, final String supersededOn,
            final String principal) {
        for (final String due : new String[] { "2026-01-31", "2026-02-28", "2026-03-31" }) {
            jdbc.update("INSERT INTO m_loan_repayment_schedule_history (loan_id, loan_reschedule_request_id, version, superseded_on_date,"
                    + " duedate, principal_amount, interest_amount, fee_charges_amount, penalty_charges_amount) VALUES (?, ?, ?, ?, ?, ?, 100, 0, 0)",
                    loanId, requestId, version, supersededOn, due, new BigDecimal(principal));
        }
    }
}

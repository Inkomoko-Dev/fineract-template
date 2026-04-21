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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.apache.fineract.portfolio.loanaccount.data.TransUnionRwandaConsumerCreditData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransUnionCrbPostConsumerCreditReadPlatformServiceImplTest {

    private static final long LAST_LOAN_ID = 100L;
    private static final int PAGE_SIZE = 25;

    @InjectMocks
    private TransUnionCrbPostConsumerCreditReadPlatformServiceImpl readPlatformService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DatabaseTypeResolver databaseTypeResolver;

    @Test
    void retrieveAllConsumerCreditsPageUsesLastNameForSurnameInMySqlQuery() {
        mockQueryResult();
        given(databaseTypeResolver.isMySQL()).willReturn(true);

        readPlatformService.retrieveAllConsumerCreditsPage(LAST_LOAN_ID, PAGE_SIZE);

        String sql = captureGeneratedSql();
        assertContainsPattern(sql, "mc\\.lastname\\s+AS surName");
        assertContainsPattern(sql, "mc\\.firstname\\s+AS foreName1");
        assertDoesNotContainPattern(sql, "mc\\.firstname\\s+AS surName");
        assertUsesSelectedActiveAddressCountry(sql);
        assertTrue(sql.contains("DATEDIFF(NOW(), mlaa.overdue_since_date_derived)"));
    }

    @Test
    void retrieveAllConsumerCreditsPageUsesLastNameForSurnameInPostgreSqlQuery() {
        mockQueryResult();
        given(databaseTypeResolver.isMySQL()).willReturn(false);

        readPlatformService.retrieveAllConsumerCreditsPage(LAST_LOAN_ID, PAGE_SIZE);

        String sql = captureGeneratedSql();
        assertContainsPattern(sql, "mc\\.lastname\\s+AS surName");
        assertContainsPattern(sql, "mc\\.firstname\\s+AS foreName1");
        assertDoesNotContainPattern(sql, "mc\\.firstname\\s+AS surName");
        assertUsesSelectedActiveAddressCountry(sql);
        assertTrue(sql.contains("EXTRACT(DAY FROM"));
    }

    private void mockQueryResult() {
        List<TransUnionRwandaConsumerCreditData> results = Collections.emptyList();
        given(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<TransUnionRwandaConsumerCreditData>>any(),
                eq(LAST_LOAN_ID), eq(PAGE_SIZE))).willReturn(results);
    }

    private String captureGeneratedSql() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), org.mockito.ArgumentMatchers.<RowMapper<TransUnionRwandaConsumerCreditData>>any(),
                eq(LAST_LOAN_ID), eq(PAGE_SIZE));
        return sqlCaptor.getValue();
    }

    private void assertContainsPattern(String sql, String regex) {
        assertTrue(Pattern.compile(regex).matcher(sql).find(), "Expected SQL to contain pattern: " + regex);
    }

    private void assertDoesNotContainPattern(String sql, String regex) {
        assertFalse(Pattern.compile(regex).matcher(sql).find(), "Expected SQL not to contain pattern: " + regex);
    }

    private void assertUsesSelectedActiveAddressCountry(String sql) {
        assertTrue(sql.contains("address_type_cv.code_value AS addressType"));
        assertTrue(sql.contains("ca.is_active = true"));
        assertTrue(sql.contains("WHEN address_type_cv.code_value = 'CURRENT ADDRESS' THEN 0 ELSE 1 END"));
        assertTrue(sql.contains("LEFT JOIN RankedAddresses ranked_address ON mc.id = ranked_address.client_id"));
        assertTrue(sql.contains("LEFT JOIN m_code_value country_cv ON ra.country_id = country_cv.id"));
        assertFalse(sql.contains("m_client_recruitment_survey"));
    }
}

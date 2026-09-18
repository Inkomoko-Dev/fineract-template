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
package org.apache.fineract.portfolio.loanclassification.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationSummaryRowData;
import org.apache.fineract.portfolio.loanclassification.domain.LoanClassificationCountryConfigRepository;
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
class LoanClassificationReadPlatformServiceImplTest {

    @InjectMocks
    private LoanClassificationReadPlatformServiceImpl readPlatformService;

    @Mock
    private LoanClassificationCountryConfigRepository countryConfigRepository;

    @Mock
    private CodeValueReadPlatformService codeValueReadPlatformService;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void summaryIsCurrentSnapshotAndIncludesLoansWithoutClassificationRows() {
        mockEmptySummary();

        readPlatformService.retrieveSummary(null, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        final String sql = captureSummarySql(false);
        assertTrue(sql.contains("FROM m_loan l"));
        assertTrue(sql.contains("LEFT JOIN m_loan_classification lc ON lc.loan_id = l.id"));
        assertTrue(sql.contains("l.loan_status_id IN (300, 601)"));
        assertTrue(sql.contains("Invalid/Missing"));
        assertTrue(sql.contains("m_client_address"));
        assertTrue(sql.contains("m_loan_due_diligence_info"));
        assertFalse(sql.contains("classified_on_utc"));
        assertFalse(sql.contains("INNER JOIN m_loan_classification"));
        assertFalse(sql.contains("FROM m_loan_classification lc INNER JOIN m_loan"));
    }

    @Test
    void summaryCountryFilterIncludesUnclassifiedLoansForThatCountry() {
        mockEmptySummary();

        readPlatformService.retrieveSummary(44L, null, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        final String sql = captureSummarySql(true);
        assertTrue(sql.contains(LoanClassificationReadPlatformServiceImpl.LOAN_COUNTRY_CV_ID + " = ?"));
        assertFalse(sql.contains("AND lc.country_cv_id = ?"));
        assertTrue(sql.contains("LEFT JOIN m_loan_classification lc ON lc.loan_id = l.id"));
        assertFalse(sql.contains("classified_on_utc"));
    }

    private void mockEmptySummary() {
        final List<LoanClassificationSummaryRowData> results = Collections.emptyList();
        given(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<LoanClassificationSummaryRowData>>any()))
                .willReturn(results);
        given(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<LoanClassificationSummaryRowData>>any(), any()))
                .willReturn(results);
    }

    private String captureSummarySql(final boolean countryFilter) {
        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        if (countryFilter) {
            verify(jdbcTemplate).query(sqlCaptor.capture(),
                    org.mockito.ArgumentMatchers.<RowMapper<LoanClassificationSummaryRowData>>any(), any());
        } else {
            verify(jdbcTemplate).query(sqlCaptor.capture(),
                    org.mockito.ArgumentMatchers.<RowMapper<LoanClassificationSummaryRowData>>any());
        }
        return sqlCaptor.getValue();
    }
}

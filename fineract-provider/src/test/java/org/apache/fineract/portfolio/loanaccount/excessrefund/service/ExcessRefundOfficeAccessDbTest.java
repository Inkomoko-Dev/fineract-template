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
package org.apache.fineract.portfolio.loanaccount.excessrefund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.portfolio.loanaccount.excessrefund.data.ExcessRefundData;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@EnabledIfEnvironmentVariable(named = "EXCESS_REFUND_TEST_JDBC_URL", matches = ".+")
class ExcessRefundOfficeAccessDbTest {

    @Test
    void restrictsDetailListAndCsvToAccessibleLoanOffices() {
        final var dataSource = new SingleConnectionDataSource(System.getenv("EXCESS_REFUND_TEST_JDBC_URL"),
                System.getenv().getOrDefault("EXCESS_REFUND_TEST_JDBC_USER", "root"),
                System.getenv().getOrDefault("EXCESS_REFUND_TEST_JDBC_PASSWORD", ""), true);
        try {
            final var jdbc = new JdbcTemplate(dataSource);
            createFixture(jdbc);
            final var context = mock(PlatformSecurityContext.class);
            final var user = mock(AppUser.class);
            final var offices = mock(OfficeHierarchyService.class);
            when(context.authenticatedUser()).thenReturn(user);
            when(offices.getUserAccessibleOffices(user)).thenReturn(List.of(1L, 2L));
            final var service = new ExcessRefundReadPlatformService(context, jdbc, null, null, null, null, offices);

            assertThat(service.retrieveOne(1L).getLoanId()).isEqualTo(1L);
            assertThat(service.retrieveAll(null, 200, 7L)).extracting(ExcessRefundData::getId).containsExactlyInAnyOrder(1L, 2L);
            assertThat(service.retrieveAll(3L, null, null)).isEmpty();
            assertThatThrownBy(() -> service.retrieveOne(3L)).isInstanceOf(GeneralPlatformDomainRuleException.class);
            assertThat(service.exportCsv(200)).contains("same-office", "child-office").doesNotContain("other-office");
        } finally {
            dataSource.destroy();
        }
    }

    private static void createFixture(final JdbcTemplate jdbc) {
        jdbc.execute("CREATE TEMPORARY TABLE m_loan (id BIGINT, account_no VARCHAR(32), office_id BIGINT, client_id BIGINT, "
                + "group_id BIGINT, total_overpaid_derived DECIMAL(19,6))");
        jdbc.execute("CREATE TEMPORARY TABLE m_client (id BIGINT, office_id BIGINT, display_name VARCHAR(32))");
        jdbc.execute("CREATE TEMPORARY TABLE m_group (id BIGINT, office_id BIGINT)");
        jdbc.execute("CREATE TEMPORARY TABLE m_appuser (id BIGINT, username VARCHAR(32))");
        jdbc.execute("CREATE TEMPORARY TABLE m_loan_excess_refund (id BIGINT, loan_id BIGINT, client_id BIGINT, batch_id BIGINT, "
                + "amount DECIMAL(19,6), currency_code VARCHAR(3), payment_mode_enum INT, payment_type_id BIGINT, status_enum INT, "
                + "beneficiary_json TEXT, hub_request_id VARCHAR(32), hub_transaction_ref VARCHAR(32), manual_payment_ref VARCHAR(32), "
                + "paid_on DATE, submitted_by BIGINT, submitted_at TIMESTAMP NULL, approved_by BIGINT, approved_at TIMESTAMP NULL, "
                + "posted_transaction_id BIGINT, failure_reason TEXT)");
        jdbc.update("INSERT INTO m_client VALUES (1, 3, 'Same'), (2, 2, 'Child'), (3, 3, 'Other')");
        jdbc.update("INSERT INTO m_loan VALUES (1, '001', 1, 1, NULL, 10), (2, '002', NULL, 2, NULL, 10), "
                + "(3, '003', 3, 3, NULL, 10)");
        for (int id = 1; id <= 3; id++) {
            final String beneficiary = List.of("same-office", "child-office", "other-office").get(id - 1);
            jdbc.update("INSERT INTO m_loan_excess_refund (id, loan_id, client_id, batch_id, amount, currency_code, "
                    + "payment_mode_enum, status_enum, beneficiary_json) VALUES (?, ?, ?, 7, 10, 'KES', 1, 200, ?)",
                    id, id, id, "{\"channel\":\"MOBILE_MONEY\",\"msisdn\":\"" + beneficiary + "\"}");
        }
    }
}

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
package org.apache.fineract.infrastructure.security.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class SQLInjectionValidatorTest {

    private static final String OFFICE_ID = "${officeId}";
    private static final String REPORT_LISTING = "${reportListing}";

    @ParameterizedTest
    @ValueSource(strings = { "1", "-1", "-10", "0", "100", "KES", "ETB", "XAF", "RWF", "2026-08-31", "2024-03-31", "today",
            "000402661", "Proj-Fin-Migr-Rw", "12.5" })
    public void acceptsTheValuesRealReportParametersCarry(final String value) {
        assertDoesNotThrow(() -> SQLInjectionValidator.validateReportParameter(OFFICE_ID, value));
    }

    @Test
    public void acceptsBlankAndNull() {
        assertDoesNotThrow(() -> SQLInjectionValidator.validateReportParameter(OFFICE_ID, null));
        assertDoesNotThrow(() -> SQLInjectionValidator.validateReportParameter(OFFICE_ID, ""));
        assertDoesNotThrow(() -> SQLInjectionValidator.validateReportParameter(REPORT_LISTING, null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1' OR '1'='1", "-1' OR 'a'='a", "1 OR 1<2", "1) OR (2>1", "benchmark(10000000,md5('a'))",
            "1,2", "1;SHOW TABLES", "%", "*", "1\nOR TRUE", "`m_loan`", "\"x\"" })
    public void rejectsCharactersThatOnlyAppearInInjectionAttempts(final String value) {
        assertThrows(SQLInjectionException.class, () -> SQLInjectionValidator.validateReportParameter(OFFICE_ID, value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1 OR TRUE", "1 AND TRUE", "1 IS NOT NULL", "1 XOR 0" })
    public void rejectsSpaceSeparatedPredicatesThatCarryNoOperatorCharacters(final String value) {
        assertThrows(SQLInjectionException.class, () -> SQLInjectionValidator.validateReportParameter(OFFICE_ID, value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "n/a", "LOAN/12763/2023", "2026-04-26 23:05:04" })
    public void keepsRejectingWhatTheSharedValidatorAlreadyRejected(final String value) {
        assertThrows(SQLInjectionException.class, () -> SQLInjectionValidator.validateReportParameter(OFFICE_ID, value));
        assertThrows(SQLInjectionException.class, () -> SQLInjectionValidator.validateSQLInput(value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1 UNION SELECT 1", "1; DROP TABLE m_loan", "1 -- comment", "sleep(5)" })
    public void stillRejectsWhatTheSharedValidatorAlreadyCaught(final String value) {
        assertThrows(SQLInjectionException.class, () -> SQLInjectionValidator.validateReportParameter(OFFICE_ID, value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "'Loan Payment Details Report'", "'Portfolio Management'", "'Active Loans - Details'",
            "'Loan Due in X days'", "'Loan Disbursed','Other Report'",
            "'WhatsApp Staff Campaign - Active Staff by Office'", "'Loan Payment Details Report','Portfolio Management'" })
    public void acceptsTheQuotedReportNamesTheParameterListIsFetchedWith(final String value) {
        assertDoesNotThrow(() -> SQLInjectionValidator.validateReportParameter(REPORT_LISTING, value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "Loan Payment Details Report", "Loan Due in X days", "'Loan Payment Details Report", "'x') OR ('a'<'b",
            "'x' OR 'a'<'b'", "'x\\'", "'a', 'b'", "'x'), (SELECT 1", "'; DROP TABLE m_loan", "'foo' OR '1'='1", "'bad'name'",
            "''", "'a';select 1" })
    public void rejectsReportListingsThatAreNotPlainQuotedNames(final String value) {
        assertThrows(SQLInjectionException.class, () -> SQLInjectionValidator.validateReportParameter(REPORT_LISTING, value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "'OR 1 OR'", "'Loan Payment Details Report'", "'1'" })
    public void quotedValuesStayRejectedForEveryOtherParameter(final String value) {
        assertThrows(SQLInjectionException.class, () -> SQLInjectionValidator.validateReportParameter(OFFICE_ID, value));
        assertThrows(SQLInjectionException.class, () -> SQLInjectionValidator.validateReportParameter("${currencyId}", value));
    }

    @Test
    public void doesNotTightenTheSharedValidatorUsedByDatatableWhereClauses() {
        assertDoesNotThrow(() -> SQLInjectionValidator.validateSQLInput("status_id = 300"));
        assertDoesNotThrow(() -> SQLInjectionValidator.validateSQLInput("amount > 100"));
    }

}

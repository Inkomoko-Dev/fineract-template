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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoanClassificationCandidateAssemblerTest {

    @Test
    void candidateSqlResolvesCountryFromOfficeBeforeClientAddress() throws Exception {
        final Method baseSql = LoanClassificationCandidateAssembler.class.getDeclaredMethod("baseSql");
        baseSql.setAccessible(true);
        final String sql = (String) baseSql.invoke(new LoanClassificationCandidateAssembler(null));
        final int officePos = sql.indexOf("FROM m_loan_classification_country_config OCFG");
        final int addrPos = sql.indexOf("FROM m_client_address");
        assertTrue(officePos >= 0, "baseSql must resolve country from the loan office hierarchy");
        assertTrue(addrPos > officePos, "office country must take precedence over the client address country");
    }

    @Test
    void candidateSqlJoinsOfficeAndUsesMatchingAliasCase() throws Exception {
        final Method baseSql = LoanClassificationCandidateAssembler.class.getDeclaredMethod("baseSql");
        baseSql.setAccessible(true);
        final String sql = (String) baseSql.invoke(new LoanClassificationCandidateAssembler(null));
        assertTrue(sql.contains("LEFT JOIN m_office o ON o.id = l.office_id"),
                "baseSql must join the loan office so the hierarchy lookup has a table to read");
        assertFalse(sql.contains("CONCAT(O.hierarchy"),
                "office alias is lowercase o; O.hierarchy is a non-existent column");
        assertTrue(sql.contains("COALESCE(o.hierarchy, co.hierarchy) LIKE CONCAT(HO.hierarchy"),
                "the loan office hierarchy must be matched against the country office ancestor");
    }

    @Test
    void candidateSqlWalksTheHierarchyUpwardToTheCountryAncestor() throws Exception {
        final Method baseSql = LoanClassificationCandidateAssembler.class.getDeclaredMethod("baseSql");
        baseSql.setAccessible(true);
        final String sql = (String) baseSql.invoke(new LoanClassificationCandidateAssembler(null));
        // The country office is an ANCESTOR of the loan office (e.g. Garissa .121.3.78. under
        // Inkomoko - Kenya .121.3.), so the loan office hierarchy must start with the country
        // office hierarchy: o.hierarchy LIKE CONCAT(HO.hierarchy, '%'). The inverted form
        // (HO.hierarchy LIKE CONCAT(o.hierarchy, '%')) only self-matches offices whose own name
        // contains the country and never resolves child offices, silently falling back to the
        // client address country.
        assertTrue(sql.contains("COALESCE(o.hierarchy, co.hierarchy) LIKE CONCAT(HO.hierarchy, '%')"),
                "country resolution must walk UP the hierarchy to the country-named ancestor");
        assertFalse(sql.contains("HO.hierarchy LIKE CONCAT(o.hierarchy, '%')"),
                "the inverted hierarchy match never finds the country ancestor of child offices");
    }

    @Test
    void candidateSqlFallsBackToTheClientOfficeWhenTheLoanHasNoOffice() throws Exception {
        final Method baseSql = LoanClassificationCandidateAssembler.class.getDeclaredMethod("baseSql");
        baseSql.setAccessible(true);
        final String sql = (String) baseSql.invoke(new LoanClassificationCandidateAssembler(null));
        // Bulk-imported loans can carry a NULL office_id while their clients live in a country
        // office (e.g. Inkomoko - Rwanda Capital .2.). The candidate query must still see them
        // (LEFT, not INNER, join on the loan office) and resolve their country from the CLIENT
        // office hierarchy before touching address or due-diligence fallbacks.
        assertTrue(sql.contains("LEFT JOIN m_office o ON o.id = l.office_id"),
                "imported loans have no office; an INNER join would silently drop them from the job");
        assertTrue(sql.contains("LEFT JOIN m_client c ON c.id = l.client_id"),
                "the client join is needed to reach the client office hierarchy");
        assertTrue(sql.contains("LEFT JOIN m_office co ON co.id = c.office_id"),
                "the client office join is the fallback hierarchy source");
        assertTrue(sql.contains("COALESCE(o.hierarchy, co.hierarchy)"),
                "country resolution must prefer the loan office and fall back to the client office");
    }

    @Test
    void noAgingRowMeansCurrentLoanIsZeroDaysNotNull() {
        assertEquals(0, LoanClassificationCandidateAssembler.resolveDaysInArrears(false, false, null, null));
    }

    @Test
    void overdueAmountWithoutOverdueSinceIsNullAndNotDefaultedToZero() {
        assertNull(LoanClassificationCandidateAssembler.resolveDaysInArrears(false, true, null, new BigDecimal("25.00")));
    }

    @Test
    void agingRowWithNullOverdueSinceAndNullAmountIsFlaggedNotZero() {
        assertNull(LoanClassificationCandidateAssembler.resolveDaysInArrears(false, true, null, null));
    }

    @Test
    void agingRowWithExplicitZeroOverdueAndNoSinceIsCurrent() {
        assertEquals(0, LoanClassificationCandidateAssembler.resolveDaysInArrears(false, true, null, BigDecimal.ZERO));
    }

    @Test
    void writtenOffDoesNotUseArrearsDays() {
        assertNull(LoanClassificationCandidateAssembler.resolveDaysInArrears(true, true, null, new BigDecimal("100")));
    }
}

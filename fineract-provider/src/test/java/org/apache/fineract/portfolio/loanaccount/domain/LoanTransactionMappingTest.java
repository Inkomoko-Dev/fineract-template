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
package org.apache.fineract.portfolio.loanaccount.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.office.domain.Office;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for LoanTransaction mapping logic, specifically for CGLT-682 fix.
 * Tests the updateLoanTransactionToRepaymentScheduleMappings method to ensure
 * correct matching by primary key ID instead of dueDate.
 */
class LoanTransactionMappingTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate SAME_DUE_DATE = LocalDate.of(2026, 6, 15);
    private static final Office OFFICE = mock(Office.class);

    private LoanTransaction loanTransaction;
    private LoanRepaymentScheduleInstallment installment1;
    private LoanRepaymentScheduleInstallment installment2;
    private LoanRepaymentScheduleInstallment installment3;

    @BeforeEach
    void setUp() {
        loanTransaction = LoanTransaction.repayment(OFFICE, Money.of(KES, new BigDecimal("50000")), null, SAME_DUE_DATE, null);
        
        // Create installments with the same due date to test primary key matching
        installment1 = new LoanRepaymentScheduleInstallment(null, 1, SAME_DUE_DATE.minusMonths(1), SAME_DUE_DATE, 
                new BigDecimal("30000"), new BigDecimal("3000"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        installment1.setId(1L);
        
        installment2 = new LoanRepaymentScheduleInstallment(null, 2, SAME_DUE_DATE, SAME_DUE_DATE.plusMonths(1), 
                new BigDecimal("20000"), new BigDecimal("2000"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        installment2.setId(2L);
        
        installment3 = new LoanRepaymentScheduleInstallment(null, 3, SAME_DUE_DATE.plusMonths(1), SAME_DUE_DATE.plusMonths(2), 
                new BigDecimal("10000"), new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        installment3.setId(3L);
    }

    @Test
    void updateLoanTransactionToRepaymentScheduleMappings_MatchesByPrimaryNotDueDate() {
        // Given: Existing mapping to installment1
        LoanTransactionToRepaymentScheduleMapping existingMapping = LoanTransactionToRepaymentScheduleMapping.createFrom(
                loanTransaction, installment1, 
                Money.of(KES, new BigDecimal("30000")), Money.of(KES, new BigDecimal("3000")), 
                Money.zero(KES), Money.zero(KES));
        loanTransaction.getLoanTransactionToRepaymentScheduleMappings().add(existingMapping);

        // When: Update with new mapping for installment2 (has same due date as installment1)
        Collection<LoanTransactionToRepaymentScheduleMapping> newMappings = new ArrayList<>();
        LoanTransactionToRepaymentScheduleMapping newMapping = LoanTransactionToRepaymentScheduleMapping.createFrom(
                loanTransaction, installment2,
                Money.of(KES, new BigDecimal("20000")), Money.of(KES, new BigDecimal("2000")),
                Money.zero(KES), Money.zero(KES));
        newMappings.add(newMapping);

        loanTransaction.updateLoanTransactionToRepaymentScheduleMappings(newMappings);

        // Then: Should have both mappings (matched by ID, not dueDate)
        assertEquals(2, loanTransaction.getLoanTransactionToRepaymentScheduleMappings().size());
        
        // Verify installment1 mapping still exists
        boolean hasInstallment1Mapping = loanTransaction.getLoanTransactionToRepaymentScheduleMappings().stream()
                .anyMatch(m -> m.getLoanRepaymentScheduleInstallment().getId().equals(1L));
        assertTrue(hasInstallment1Mapping);
        
        // Verify installment2 mapping was added
        boolean hasInstallment2Mapping = loanTransaction.getLoanTransactionToRepaymentScheduleMappings().stream()
                .anyMatch(m -> m.getLoanRepaymentScheduleInstallment().getId().equals(2L));
        assertTrue(hasInstallment2Mapping);
    }

    @Test
    void updateLoanTransactionToRepaymentScheduleMappings_UpdatesExistingMappingByPrimary() {
        // Given: Existing mapping to installment1
        LoanTransactionToRepaymentScheduleMapping existingMapping = LoanTransactionToRepaymentScheduleMapping.createFrom(
                loanTransaction, installment1,
                Money.of(KES, new BigDecimal("30000")), Money.of(KES, new BigDecimal("3000")),
                Money.zero(KES), Money.zero(KES));
        loanTransaction.getLoanTransactionToRepaymentScheduleMappings().add(existingMapping);

        // When: Update with new amounts for same installment1
        Collection<LoanTransactionToRepaymentScheduleMapping> newMappings = new ArrayList<>();
        LoanTransactionToRepaymentScheduleMapping updatedMapping = LoanTransactionToRepaymentScheduleMapping.createFrom(
                loanTransaction, installment1,
                Money.of(KES, new BigDecimal("35000")), Money.of(KES, new BigDecimal("3500")),
                Money.zero(KES), Money.zero(KES));
        newMappings.add(updatedMapping);

        loanTransaction.updateLoanTransactionToRepaymentScheduleMappings(newMappings);

        // Then: Should update existing mapping, not create duplicate
        assertEquals(1, loanTransaction.getLoanTransactionToRepaymentScheduleMappings().size());
        
        LoanTransactionToRepaymentScheduleMapping result = loanTransaction.getLoanTransactionToRepaymentScheduleMappings().iterator().next();
        assertEquals(0, new BigDecimal("35000").compareTo(result.getPrincipalPortion()));
        assertEquals(0, new BigDecimal("3500").compareTo(result.getInterestPortion()));
    }

    @Test
    void updateLoanTransactionToRepaymentScheduleMappings_HandlesNullInstallmentId() {
        // Given: New mapping with null installment ID (new installment not yet persisted)
        LoanRepaymentScheduleInstallment newInstallment = new LoanRepaymentScheduleInstallment(null, 4, 
                SAME_DUE_DATE.plusMonths(2), SAME_DUE_DATE.plusMonths(3),
                new BigDecimal("5000"), new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        
        Collection<LoanTransactionToRepaymentScheduleMapping> newMappings = new ArrayList<>();
        LoanTransactionToRepaymentScheduleMapping newMapping = LoanTransactionToRepaymentScheduleMapping.createFrom(
                loanTransaction, newInstallment,
                Money.of(KES, new BigDecimal("5000")), Money.of(KES, new BigDecimal("500")),
                Money.zero(KES), Money.zero(KES));
        newMappings.add(newMapping);

        // When: Update with null ID
        loanTransaction.updateLoanTransactionToRepaymentScheduleMappings(newMappings);

        // Then: Should still add the mapping (fallback behavior)
        assertEquals(1, loanTransaction.getLoanTransactionToRepaymentScheduleMappings().size());
        assertNotNull(loanTransaction.getLoanTransactionToRepaymentScheduleMappings().iterator().next());
    }

    @Test
    void updateLoanTransactionToRepaymentScheduleMappings_MultipleInstallmentsSameDueDate() {
        // Given: Create two installments with intentionally same due date (edge case)
        LoanRepaymentScheduleInstallment installmentA = new LoanRepaymentScheduleInstallment(null, 5, 
                SAME_DUE_DATE, SAME_DUE_DATE,
                new BigDecimal("10000"), new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        installmentA.setId(5L);
        
        LoanRepaymentScheduleInstallment installmentB = new LoanRepaymentScheduleInstallment(null, 6, 
                SAME_DUE_DATE, SAME_DUE_DATE,
                new BigDecimal("15000"), new BigDecimal("1500"), BigDecimal.ZERO, BigDecimal.ZERO, false, null);
        installmentB.setId(6L);

        // When: Create mappings for both installments with same due date
        Collection<LoanTransactionToRepaymentScheduleMapping> newMappings = new ArrayList<>();
        newMappings.add(LoanTransactionToRepaymentScheduleMapping.createFrom(
                loanTransaction, installmentA,
                Money.of(KES, new BigDecimal("10000")), Money.of(KES, new BigDecimal("1000")),
                Money.zero(KES), Money.zero(KES)));
        newMappings.add(LoanTransactionToRepaymentScheduleMapping.createFrom(
                loanTransaction, installmentB,
                Money.of(KES, new BigDecimal("15000")), Money.of(KES, new BigDecimal("1500")),
                Money.zero(KES), Money.zero(KES)));

        loanTransaction.updateLoanTransactionToRepaymentScheduleMappings(newMappings);

        // Then: Should have both distinct mappings based on primary key
        assertEquals(2, loanTransaction.getLoanTransactionToRepaymentScheduleMappings().size());
        
        boolean hasInstallmentA = loanTransaction.getLoanTransactionToRepaymentScheduleMappings().stream()
                .anyMatch(m -> m.getLoanRepaymentScheduleInstallment().getId().equals(5L));
        boolean hasInstallmentB = loanTransaction.getLoanTransactionToRepaymentScheduleMappings().stream()
                .anyMatch(m -> m.getLoanRepaymentScheduleInstallment().getId().equals(6L));
        
        assertTrue(hasInstallmentA);
        assertTrue(hasInstallmentB);
    }

    @Test
    void updateLoanTransactionToRepaymentScheduleMappings_RetainsOnlyMappingsInUpdate() {
        // Given: Existing mappings to installment1 and installment2
        loanTransaction.getLoanTransactionToRepaymentScheduleMappings().add(
                LoanTransactionToRepaymentScheduleMapping.createFrom(
                        loanTransaction, installment1,
                        Money.of(KES, new BigDecimal("30000")), Money.of(KES, new BigDecimal("3000")),
                        Money.zero(KES), Money.zero(KES)));
        loanTransaction.getLoanTransactionToRepaymentScheduleMappings().add(
                LoanTransactionToRepaymentScheduleMapping.createFrom(
                        loanTransaction, installment2,
                        Money.of(KES, new BigDecimal("20000")), Money.of(KES, new BigDecimal("2000")),
                        Money.zero(KES), Money.zero(KES)));

        // When: Update with only installment3 mapping
        Collection<LoanTransactionToRepaymentScheduleMapping> newMappings = new ArrayList<>();
        newMappings.add(LoanTransactionToRepaymentScheduleMapping.createFrom(
                loanTransaction, installment3,
                Money.of(KES, new BigDecimal("10000")), Money.of(KES, new BigDecimal("1000")),
                Money.zero(KES), Money.zero(KES)));

        loanTransaction.updateLoanTransactionToRepaymentScheduleMappings(newMappings);

        // Then: Should only retain installment3 mapping (demonstrates retainAll behavior)
        assertEquals(1, loanTransaction.getLoanTransactionToRepaymentScheduleMappings().size());
        
        LoanTransactionToRepaymentScheduleMapping result = loanTransaction.getLoanTransactionToRepaymentScheduleMappings().iterator().next();
        assertEquals(3L, result.getLoanRepaymentScheduleInstallment().getId());
    }
}

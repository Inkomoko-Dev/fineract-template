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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.portfolio.loanaccount.data.LoanTransactionEnumData;
import org.apache.fineract.portfolio.loanproduct.service.LoanEnumerations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoanTransactionTypeLabelTest {

    @ParameterizedTest
    @EnumSource(value = LoanTransactionType.class, mode = EnumSource.Mode.EXCLUDE, names = "INVALID")
    void everyStoredTypeCodeResolvesBackToItself(LoanTransactionType type) {
        assertEquals(type, LoanTransactionType.fromInt(type.getValue()));
    }

    @ParameterizedTest
    @EnumSource(value = LoanTransactionType.class, mode = EnumSource.Mode.EXCLUDE, names = "INVALID")
    void noLegitimateTypeIsLabelledInvalid(LoanTransactionType type) {
        LoanTransactionEnumData data = LoanEnumerations.transactionType(type.getValue());
        assertEquals(type.getValue().longValue(), data.id());
        assertNotEquals("Invalid", data.getValue());
        assertNotEquals(LoanTransactionType.INVALID.getCode(), data.getCode());
    }

    @Test
    void typeCodesAreUnique() {
        Set<Integer> codes = new HashSet<>();
        Arrays.stream(LoanTransactionType.values()).forEach(type -> assertTrue(codes.add(type.getValue()), type.name()));
    }

    @Test
    void futureInterestCancellationIsStoredAs34AndLabelledForTheTransactionsTab() {
        LoanTransactionEnumData data = LoanEnumerations.transactionType(34);
        assertEquals("Future Interest Cancellation", data.getValue());
        assertEquals("loanTransactionType.futureInterestCancellation", data.getCode());
        assertTrue(data.isFutureInterestCancellation());
        assertFalse(data.isInsuranceChargeAdjustment());
    }

    @Test
    void insuranceChargeAdjustmentKeepsCode30() {
        LoanTransactionEnumData data = LoanEnumerations.transactionType(30);
        assertEquals("Insurance Charge Adjustment", data.getValue());
        assertTrue(data.isInsuranceChargeAdjustment());
        assertFalse(data.isFutureInterestCancellation());
    }
}

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
package org.apache.fineract.portfolio.loanaccount.serialization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the multi-disbursement validation in {@link LoanApplicationCommandFromApiJsonHelper}.
 *
 * <p>
 * Reproduces CGLT-641: a flat-interest multi-disburse loan could not have its repayment terms modified because the
 * multi-disbursement validation forced {@code interestType == DECLINING_BALANCE}, surfacing in the UI as
 * "Flat interest type is not allowed for multi disburse loan." (backend error code
 * {@code validation.msg.loan.interestType.not.equal.to.specified.number}).
 * </p>
 */
public class LoanApplicationMultiDisburseFlatInterestTest {

    private static final String INTEREST_TYPE_NOT_DECLINING_ERROR = "validation.msg.loan.interestType.not.equal.to.specified.number";

    private static final int FLAT = 1;
    private static final int DECLINING_BALANCE = 0;

    private final LoanApplicationCommandFromApiJsonHelper helper = new LoanApplicationCommandFromApiJsonHelper(new FromJsonHelper(), null,
            null, null);

    private String multiDisburseJson(final int interestType) {
        return "{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"interestType\":" + interestType + ","
                + "\"disbursementData\":["
                + "{\"expectedDisbursementDate\":\"01 January 2024\",\"principal\":5000},"
                + "{\"expectedDisbursementDate\":\"01 February 2024\",\"principal\":5000}]}";
    }

    private List<ApiParameterError> validate(final int interestType) {
        final JsonElement element = new FromJsonHelper().parse(multiDisburseJson(interestType));
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");
        helper.validateLoanMultiDisbursementDate(element, baseDataValidator, LocalDate.of(2024, 1, 1), new BigDecimal("10000"));
        return errors;
    }

    private boolean hasInterestTypeError(final List<ApiParameterError> errors) {
        return errors.stream().anyMatch(e -> INTEREST_TYPE_NOT_DECLINING_ERROR.equals(e.getUserMessageGlobalisationCode()));
    }

    @Test
    public void flatInterestIsAllowedForMultiDisburseLoan() {
        // CGLT-641: a flat-interest multi-disburse payload must NOT be rejected by the interestType validation.
        assertFalse(hasInterestTypeError(validate(FLAT)),
                "Flat interest type must be allowed for multi disburse loans (CGLT-641); validation should not emit "
                        + INTEREST_TYPE_NOT_DECLINING_ERROR);
    }

    @Test
    public void decliningBalanceMultiDisburseRemainsValid() {
        // Regression guard: declining-balance multi-disburse keeps validating cleanly.
        assertFalse(hasInterestTypeError(validate(DECLINING_BALANCE)));
    }

    @Test
    public void singleDisburseFlatLoanIsUnaffected() {
        // No disbursementData array -> the multi-disburse block (and its interestType check) never runs.
        final JsonElement element = new FromJsonHelper().parse("{\"locale\":\"en\",\"dateFormat\":\"dd MMMM yyyy\",\"interestType\":" + FLAT + "}");
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("loan");
        helper.validateLoanMultiDisbursementDate(element, baseDataValidator, LocalDate.of(2024, 1, 1), new BigDecimal("10000"));
        assertTrue(errors.isEmpty(), "Single-disburse flat loan should not trigger multi-disbursement validation");
    }
}

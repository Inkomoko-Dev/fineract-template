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
package org.apache.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;

/** A historical penalty waiver request, lifted out of the JSON so the service never has to parse. */
public class HistoricalPenaltyWaiverRequest {

    private final Long commandId;
    private final BigDecimal expectedPaidAmount;
    private final BigDecimal waiverAmount;
    private final LocalDate waiverEffectiveDate;
    private final String reason;
    private final Integer installmentNumber;
    private final Long nextApproverUserId;

    public HistoricalPenaltyWaiverRequest(final Long commandId, final BigDecimal expectedPaidAmount, final BigDecimal waiverAmount,
            final LocalDate waiverEffectiveDate, final String reason, final Integer installmentNumber, final Long nextApproverUserId) {
        this.commandId = commandId;
        this.expectedPaidAmount = expectedPaidAmount;
        this.waiverAmount = waiverAmount;
        this.waiverEffectiveDate = waiverEffectiveDate;
        this.reason = reason;
        this.installmentNumber = installmentNumber;
        this.nextApproverUserId = nextApproverUserId;
    }

    public static HistoricalPenaltyWaiverRequest from(final JsonCommand command) {
        return new HistoricalPenaltyWaiverRequest(command.commandId(),
                command.bigDecimalValueOfParameterNamed(LoanApiConstants.expectedPaidAmountParamName),
                command.bigDecimalValueOfParameterNamed(LoanApiConstants.waiverAmountParamName),
                command.localDateValueOfParameterNamed(LoanApiConstants.waiverEffectiveDateParamName),
                command.stringValueOfParameterNamed(LoanApiConstants.reasonParamName),
                command.integerValueOfParameterNamed("installmentNumber"),
                command.longValueOfParameterNamed(LoanApiConstants.nextApproverUserIdParamName));
    }

    public Long getCommandId() {
        return this.commandId;
    }

    public BigDecimal getExpectedPaidAmount() {
        return this.expectedPaidAmount;
    }

    /** Null means waive whatever the penalty is worth. */
    public BigDecimal getWaiverAmount() {
        return this.waiverAmount;
    }

    public LocalDate getWaiverEffectiveDate() {
        return this.waiverEffectiveDate;
    }

    public String getReason() {
        return this.reason;
    }

    public Integer getInstallmentNumber() {
        return this.installmentNumber;
    }

    public Long getNextApproverUserId() {
        return this.nextApproverUserId;
    }
}

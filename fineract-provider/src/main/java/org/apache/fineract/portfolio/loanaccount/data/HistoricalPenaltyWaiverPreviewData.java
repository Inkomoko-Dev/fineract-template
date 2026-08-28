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

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.apache.fineract.useradministration.data.AppUserData;

/** Everything the reviewer needs before committing a historical penalty waiver, produced by a rolled-back dry run. */
@Getter
@Builder
public class HistoricalPenaltyWaiverPreviewData implements Serializable {

    private final Long loanId;
    private final Long loanChargeId;
    private final String chargeName;
    private final LocalDate chargeDueDate;
    private final Integer chargeAgeInDays;

    private final BigDecimal chargeAmount;
    private final BigDecimal chargeAmountPaidBefore;
    private final BigDecimal chargeAmountWaivedBefore;
    private final BigDecimal chargeAmountOutstandingBefore;
    private final BigDecimal chargeAmountWaivedAfter;
    private final BigDecimal chargeAmountOutstandingAfter;

    private final BigDecimal waiverAmount;
    private final boolean partialWaiver;
    private final LocalDate waiverEffectiveDate;
    /** Transaction date of the earliest live repayment that paid this penalty. */
    private final LocalDate suggestedEffectiveDate;

    private final LoanStatusEnumData loanStatusBefore;
    private final LoanStatusEnumData loanStatusAfter;
    private final BigDecimal totalOutstandingBefore;
    private final BigDecimal totalOutstandingAfter;

    private final String transactionProcessingStrategyName;
    private final Integer reprocessedTransactionCount;
    private final List<HistoricalPenaltyWaiverTransactionImpactData> transactions;

    private final boolean requiresApproval;
    private final String approvalTrigger;
    private final boolean nextApproverRequired;
    private final Collection<AppUserData> approverOptions;

    /**
     * False when the effective date falls in a closed accounting period and corrections there are switched off. The
     * write path would otherwise do all of the work and then throw at journal-entry time.
     */
    private final boolean correctionAllowed;
    private final LocalDate latestClosureDate;
}

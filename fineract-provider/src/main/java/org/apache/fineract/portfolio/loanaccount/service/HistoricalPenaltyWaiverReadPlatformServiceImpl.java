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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverData;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverTxnData;
import org.apache.fineract.portfolio.loanaccount.domain.HistoricalPenaltyWaiverStatus;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiver;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverTxnRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanproduct.domain.HistoricalCorrectionProductApprover;
import org.apache.fineract.portfolio.loanproduct.domain.HistoricalCorrectionProductApproverRepository;
import org.apache.fineract.useradministration.data.AppUserData;
import org.apache.fineract.useradministration.service.AppUserReadPlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoricalPenaltyWaiverReadPlatformServiceImpl implements HistoricalPenaltyWaiverReadPlatformService {

    public static final String APPROVE_PERMISSION = "APPROVE_HISTORICALPENALTYWAIVER";

    private final LoanHistoricalPenaltyWaiverRepository waiverRepository;
    private final LoanHistoricalPenaltyWaiverTxnRepository waiverTxnRepository;
    private final HistoricalCorrectionProductApproverRepository approverRepository;
    private final AppUserReadPlatformService appUserReadPlatformService;
    private final LoanRepositoryWrapper loanRepositoryWrapper;

    @Override
    public HistoricalPenaltyWaiverData retrieveOne(final Long waiverId) {
        final LoanHistoricalPenaltyWaiver waiver = retrieveWaiverBy(waiverId);
        final List<HistoricalPenaltyWaiverTxnData> transactions = this.waiverTxnRepository.findByWaiverId(waiverId).stream()
                .map(HistoricalPenaltyWaiverTxnData::from).collect(Collectors.toList());
        return HistoricalPenaltyWaiverData.from(waiver, transactions);
    }

    @Override
    public List<HistoricalPenaltyWaiverData> retrieveByLoanId(final Long loanId) {
        return this.waiverRepository.findByLoanIdOrderByIdDesc(loanId).stream().map(HistoricalPenaltyWaiverData::from)
                .collect(Collectors.toList());
    }

    @Override
    public List<HistoricalPenaltyWaiverData> retrievePendingApprovalQueue() {
        return this.waiverRepository.findByStatusOrderByIdAsc(HistoricalPenaltyWaiverStatus.PENDING_APPROVAL).stream()
                .map(HistoricalPenaltyWaiverData::from).collect(Collectors.toList());
    }

    @Override
    public Collection<AppUserData> retrieveApproverOptions(final Long loanId) {

        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(loanId);

        // The office-hierarchy clause already returns users at or above the loan's office, which is what makes
        // "escalate to a higher user" fall out without a second query.
        final Collection<AppUserData> permitted = this.appUserReadPlatformService.retrieveUsersByOfficeAndPermission(loan.getOfficeId(),
                APPROVE_PERMISSION);

        final Set<Long> productApprovers = this.approverRepository.findByLoanProductId(loan.productId()).stream()
                .map(HistoricalCorrectionProductApprover::getAppUserId).collect(Collectors.toSet());

        final Collection<AppUserData> options = new ArrayList<>();
        for (final AppUserData user : permitted) {
            if (productApprovers.stream().anyMatch(user::hasIdentifyOf)) {
                options.add(user);
            }
        }
        return options;
    }

    private LoanHistoricalPenaltyWaiver retrieveWaiverBy(final Long waiverId) {
        return this.waiverRepository.findById(waiverId).orElseThrow(() -> {
            final List<ApiParameterError> errors = new ArrayList<>();
            errors.add(ApiParameterError.parameterError("error.msg.loan.charge.historical.waiver.not.found",
                    "No historical penalty waiver exists with identifier " + waiverId + ".", "id", waiverId));
            return new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.", errors);
        });
    }
}

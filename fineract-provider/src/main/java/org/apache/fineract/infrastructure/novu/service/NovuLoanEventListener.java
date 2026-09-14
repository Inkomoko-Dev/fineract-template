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
package org.apache.fineract.infrastructure.novu.service;

import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.businessevent.BusinessEventListener;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanApprovedBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanCloseBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanCreatedBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanDisbursalBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanRejectedBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.transaction.LoanTransactionMakeRepaymentPostBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NovuLoanEventListener {

    private final BusinessEventNotifierService notifier;
    private final NovuCampaignService campaignService;

    public NovuLoanEventListener(final BusinessEventNotifierService notifier, final NovuCampaignService campaignService) {
        this.notifier = notifier;
        this.campaignService = campaignService;
    }

    @PostConstruct
    public void registerListeners() {
        notifier.addPostBusinessEventListener(LoanCreatedBusinessEvent.class, loanListener("LOAN_CREATED"));
        notifier.addPostBusinessEventListener(LoanApprovedBusinessEvent.class, loanListener("LOAN_APPROVED"));
        notifier.addPostBusinessEventListener(LoanDisbursalBusinessEvent.class, loanListener("LOAN_DISBURSED"));
        notifier.addPostBusinessEventListener(LoanRejectedBusinessEvent.class, loanListener("LOAN_REJECTED"));
        notifier.addPostBusinessEventListener(LoanCloseBusinessEvent.class, loanListener("LOAN_CLOSED"));
        notifier.addPostBusinessEventListener(LoanTransactionMakeRepaymentPostBusinessEvent.class,
                event -> safeTrigger("LOAN_REPAYMENT", event.get().getLoan(), Map.of("transactionId",
                        event.get().getId(), "transactionAmount", event.get().getAmount(event.get().getLoan().getCurrency()).getAmount())));
    }

    private <T extends org.apache.fineract.portfolio.businessevent.domain.loan.LoanBusinessEvent> BusinessEventListener<T> loanListener(
            final String eventType) {
        return event -> safeTrigger(eventType, event.get(), Map.of());
    }

    private void safeTrigger(final String eventType, final org.apache.fineract.portfolio.loanaccount.domain.Loan loan,
            final Map<String, Object> payload) {
        try {
            campaignService.triggerLoanEvent(eventType, loan, payload);
        } catch (final RuntimeException e) {
            // Notification failures must never make the underlying loan operation fail.
            log.error("Unable to trigger Novu workflow for {} on loan {}", eventType, loan.getId(), e);
        }
    }
}

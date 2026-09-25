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

import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.businessevent.BusinessEventListener;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanAdjustTransactionBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.transaction.LoanTransactionMakeRepaymentPostBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.apache.fineract.portfolio.loanaccount.domain.ThirdPartyDisbursementRepaymentEvent;
import org.apache.fineract.portfolio.loanaccount.domain.ThirdPartyDisbursementRepaymentEventRepository;
import org.apache.fineract.portfolio.loanproduct.domain.ThirdPartyDisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.event.DisbursementPartnerWebhookPublisher;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class ThirdPartyDisbursementRepaymentEventService {

    private final BusinessEventNotifierService businessEventNotifierService;
    private final ThirdPartyDisbursementRepaymentEventRepository eventRepository;
    private final DisbursementPartnerWebhookPublisher webhookPublisher;

    @PostConstruct
    public void subscribe() {
        this.businessEventNotifierService.addPostBusinessEventListener(LoanTransactionMakeRepaymentPostBusinessEvent.class,
                new RepaymentListener());
        this.businessEventNotifierService.addPostBusinessEventListener(LoanAdjustTransactionBusinessEvent.class, new AdjustmentListener());
    }

    @Transactional
    public void enqueueRepayment(final LoanTransaction transaction) {
        if (transaction == null || !LoanTransactionType.REPAYMENT.equals(transaction.getTypeOf()) || transaction.isReversed()
                || transaction.isReversalTransaction()) {
            return;
        }
        enqueue(transaction, "REPAYMENT");
    }

    @Transactional
    public void enqueueReversal(final LoanTransaction transaction) {
        if (transaction == null || !LoanTransactionType.REPAYMENT.equals(transaction.getTypeOf()) || !transaction.isReversalTransaction()) {
            return;
        }
        enqueue(transaction, "REPAYMENT_REVERSAL");
    }

    private void enqueue(final LoanTransaction transaction, final String action) {
        final Loan loan = transaction.getLoan();
        final String partnerCode = loan == null ? null : ThirdPartyDisbursementProvider.normalize(loan.getThirdPartyDisbursementProvider());
        if (partnerCode == null || this.eventRepository.existsByLoanTransactionIdAndAction(transaction.getId(), action)) {
            return;
        }
        final String eventId = UUID.randomUUID().toString();
        this.eventRepository.save(new ThirdPartyDisbursementRepaymentEvent(transaction.getId(), partnerCode, eventId, action,
                eventPayload(transaction, partnerCode, action, eventId)));
    }

    private static String eventPayload(final LoanTransaction transaction, final String partnerCode, final String action,
            final String eventId) {
        final Loan loan = transaction.getLoan();
        final JsonObject payload = new JsonObject();
        payload.addProperty("loanId", loan.getId());
        payload.addProperty("loanAccountNo", loan.getAccountNumber());
        payload.addProperty("clientId", loan.getClient() == null ? null : loan.getClient().getId());
        payload.addProperty("transactionId", transaction.getId());
        payload.addProperty("originalTransactionId", transaction.getOriginalTransactionId());
        payload.addProperty("amount", transaction.getAmount(loan.getCurrency()).getAmount());
        payload.addProperty("principalPortion", transaction.getPrincipalPortion());
        payload.addProperty("interestPortion", transaction.getInterestPortion(loan.getCurrency()).getAmount());
        payload.addProperty("feePortion", transaction.getFeeChargesPortion(loan.getCurrency()).getAmount());
        payload.addProperty("penaltyPortion", transaction.getPenaltyChargesPortion(loan.getCurrency()).getAmount());
        payload.addProperty("outstandingBalance", transaction.getOutstandingLoanBalance());
        payload.addProperty("currencyCode", loan.getCurrency().getCode());
        payload.addProperty("transactionDate", transaction.getTransactionDate().toString());
        payload.addProperty("reversed", transaction.isReversed() || transaction.isReversalTransaction());
        payload.addProperty("reversalTransaction", transaction.isReversalTransaction());
        final JsonObject envelope = new JsonObject();
        envelope.addProperty("eventId", eventId);
        envelope.addProperty("eventType", "CBS_LOAN_REPAYMENT");
        envelope.addProperty("action", action);
        envelope.addProperty("occurredAt", Instant.now().toString());
        envelope.addProperty("source", "CBS");
        envelope.addProperty("partnerCode", partnerCode);
        envelope.addProperty("schemaVersion", 1);
        envelope.add("payload", payload);
        return envelope.toString();
    }


    @Scheduled(fixedDelayString = "${fineract.third-party-disbursement.repayment-webhook-delay-ms:60000}")
    @Transactional
    public void deliverPending() {
        final List<ThirdPartyDisbursementRepaymentEvent> pending = this.eventRepository.findPending(OffsetDateTime.now(ZoneOffset.UTC));
        for (final ThirdPartyDisbursementRepaymentEvent event : pending) {
            if (this.webhookPublisher.publishRaw(event.getPartnerCode(), event.getEventId(), event.getPayload())) {
                event.markDelivered();
            } else {
                event.markRetry("Partner webhook delivery failed or is not configured.");
            }
        }
    }

    private final class RepaymentListener implements BusinessEventListener<LoanTransactionMakeRepaymentPostBusinessEvent> {
        @Override
        public void onBusinessEvent(final LoanTransactionMakeRepaymentPostBusinessEvent event) {
            enqueueRepayment(event.get());
        }
    }

    private final class AdjustmentListener implements BusinessEventListener<LoanAdjustTransactionBusinessEvent> {
        @Override
        public void onBusinessEvent(final LoanAdjustTransactionBusinessEvent event) {
            enqueueReversal(event.get().getNewTransactionDetail());
        }
    }
}

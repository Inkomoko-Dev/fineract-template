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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionOutcome;
import org.apache.fineract.portfolio.creditdecision.service.CreditDecisionLoanService;
import org.apache.fineract.portfolio.creditdecision.service.CreditDecisionOutcomeRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carries out the INELIGIBLE directive of the credit decision process flow: when the decision engine finds an application
 * ineligible and the profile is Live, the application is rejected. Shadow mode only records the outcome.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditDecisionRejectionListener {

    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final LoanApplicationWritePlatformService loans;
    private final CreditDecisionLoanService creditDecisionLoanService;
    private final FromJsonHelper fromJsonHelper;
    private final PlatformTransactionManager transactionManager;

    @EventListener
    public void onOutcome(final CreditDecisionOutcomeRecorded event) {
        if (!event.isLive() || !CreditDecisionOutcome.INELIGIBLE.equals(event.getDecision())) {
            return;
        }
        final Long loanId = event.getLoanId();
        final JsonObject body = new JsonObject();
        body.addProperty("rejectedOnDate", DateUtils.getBusinessLocalDate().toString());
        body.addProperty("note", "Rejected automatically: the decision engine found the application INELIGIBLE"
                + (event.getReasonCodes() == null || event.getReasonCodes().isBlank() ? "" : " (" + event.getReasonCodes() + ")"));
        body.addProperty("locale", "en");
        body.addProperty("dateFormat", DATE_FORMAT);
        try {
            final String json = body.toString();
            final JsonCommand command = JsonCommand.from(json, fromJsonHelper.parse(json), fromJsonHelper, "LOAN", loanId, null, null,
                    null, loanId, null, null, null, null, null, null);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> loans.rejectApplication(loanId, command));
            creditDecisionLoanService.recordRejection(loanId, true, "The application was rejected automatically");
        } catch (final RuntimeException e) {
            // the application stays where it is, and the person is told why it could not be rejected
            log.warn("Could not reject loan {} after an INELIGIBLE outcome: {}", loanId, e.getMessage());
            creditDecisionLoanService.recordRejection(loanId, false,
                    "The application could not be rejected automatically, reject it by hand: " + e.getMessage());
        }
    }
}

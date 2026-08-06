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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.service.GmailBackedPlatformEmailService;
import org.apache.fineract.infrastructure.core.service.PlatformEmailSendException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiver;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.stereotype.Service;

/**
 * Emails for the historical penalty waiver approval flow. Every send is wrapped, because a mail server outage must
 * never roll back a correction that has already been applied to the ledger.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalPenaltyWaiverNotificationService {

    private final GmailBackedPlatformEmailService emailService;
    private final AppUserRepository appUserRepository;

    public void notifyApprovalRequested(final LoanHistoricalPenaltyWaiver waiver) {
        final AppUser approver = findUser(waiver.getNextApproverId());
        if (approver == null) {
            return;
        }
        send(approver, "Historical penalty waiver awaiting your approval",
                "A historical penalty waiver on loan " + waiver.getLoanId() + " needs your approval.\n\n" + summary(waiver)
                        + "\nApproval is required because: " + describeTrigger(waiver.getApprovalTrigger()));
    }

    public void notifyApproved(final LoanHistoricalPenaltyWaiver waiver) {
        final AppUser submitter = findUser(waiver.getSubmittedById());
        if (submitter == null) {
            return;
        }
        send(submitter, "Historical penalty waiver approved",
                "Your historical penalty waiver on loan " + waiver.getLoanId() + " has been approved and applied.\n\n" + summary(waiver));
    }

    public void notifyRejected(final LoanHistoricalPenaltyWaiver waiver) {
        final AppUser submitter = findUser(waiver.getSubmittedById());
        if (submitter == null) {
            return;
        }
        send(submitter, "Historical penalty waiver rejected", "Your historical penalty waiver on loan " + waiver.getLoanId()
                + " was rejected.\n\n" + summary(waiver) + "\nReason given: " + waiver.getDecisionReason());
    }

    public void notifyEscalated(final LoanHistoricalPenaltyWaiver waiver, final AppUser escalatedTo) {
        if (escalatedTo == null) {
            return;
        }
        send(escalatedTo, "Historical penalty waiver escalated to you", "A historical penalty waiver on loan " + waiver.getLoanId()
                + " has been waiting for approval and is now escalated to you.\n\n" + summary(waiver));
    }

    private String summary(final LoanHistoricalPenaltyWaiver waiver) {
        return "Reference: " + waiver.getCorrectionReference() + "\nPenalty: " + waiver.getChargeName() + "\nAmount to waive: "
                + waiver.getWaiverAmount() + "\nEffective date: " + waiver.getWaiverEffectiveDate() + "\nReason: " + waiver.getReason()
                + "\n";
    }

    private String describeTrigger(final String trigger) {
        if ("AMOUNT".equals(trigger)) {
            return "the amount exceeds the configured threshold.";
        }
        if ("AGE".equals(trigger)) {
            return "the penalty is older than the configured limit.";
        }
        if ("BOTH".equals(trigger)) {
            return "the amount exceeds the configured threshold and the penalty is older than the configured limit.";
        }
        return "a configured approval threshold was crossed.";
    }

    private AppUser findUser(final Long userId) {
        return userId == null ? null : this.appUserRepository.findById(userId).orElse(null);
    }

    private void send(final AppUser recipient, final String subject, final String body) {
        if (StringUtils.isBlank(recipient.getEmail())) {
            log.warn("User {} has no email address; historical penalty waiver notification not sent.", recipient.getId());
            return;
        }
        try {
            this.emailService.sendDefinedEmail(new EmailDetail(subject, body, recipient.getEmail(), recipient.getUsername()));
        } catch (final PlatformEmailSendException e) {
            log.error("Historical penalty waiver notification could not be sent to {}. The correction itself was unaffected. "
                    + "Check SMTP settings under Admin > System > External Services.", recipient.getEmail(), e);
        }
    }
}

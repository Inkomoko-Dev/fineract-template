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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRescheduleAlertService {

    private final NotificationWritePlatformService notificationService;
    private final PlatformEmailService emailService;
    private final AppUserRepository appUserRepository;

    @Value("${mifos.system.base-url}")
    private String baseUrl;

    public void notifySubmittedForApproval(final BulkRescheduleExecution execution, final AppUser initiator,
            final AppUser approver, final String reason) {
        notificationService.notify(approver.getId(), "BULK_RESCHEDULE", execution.getId(), "SUBMIT_FOR_APPROVAL",
                initiator.getId(),
                "Bulk reschedule request #" + execution.getId() + " requires your approval. Reason: " + reason, true);
        if (!hasEmail(approver)) {
            log.warn("Bulk reschedule request {} was submitted, but approver {} has no email address", execution.getId(),
                    approver.getId());
            return;
        }
        final String requestUrl = requestUrl(execution.getId());
        final String subject = "Bulk reschedule request #" + execution.getId() + " requires approval";
        final String body = "Dear " + escape(displayName(approver)) + ",<br><br>"
                + "A Bulk reschedule request was submitted by " + escape(displayName(initiator)) + ".<br>"
                + "Reason: " + escape(reason) + "<br><br>"
                + "Please <a href=\"" + escape(requestUrl) + "\">review the request</a> and approve or reject it.<br><br>"
                + "Kind regards.";
        sendEmail(subject, body, approver, initiator);
    }

    public void notifyExecutionCompleted(final BulkRescheduleExecution execution, final AppUser actor, final int succeeded,
            final int failed) {
        final AppUser creator = loadUser(execution.getUser());
        final AppUser approver = loadUser(execution.getApprover());
        final String content = String.format("Bulk reschedule request #%d finished: %d succeeded, %d failed.",
                execution.getId(), succeeded, failed);
        final Set<Long> recipients = new LinkedHashSet<>();
        if (creator != null) {
            recipients.add(creator.getId());
        }
        if (approver != null) {
            recipients.add(approver.getId());
        }
        if (!recipients.isEmpty()) {
            notificationService.notify(recipients, "BULK_RESCHEDULE", execution.getId(), "EXECUTE",
                    actor == null ? null : actor.getId(), content, true);
        }
        if (creator == null || !hasEmail(creator)) {
            log.warn("Bulk reschedule request {} completed, but the creator has no email address", execution.getId());
            return;
        }
        final String requestUrl = requestUrl(execution.getId());
        final String subject = "Bulk reschedule request #" + execution.getId() + " is complete";
        final String body = "Dear " + escape(displayName(creator)) + ",<br><br>"
                + "Bulk reschedule request <strong>#" + execution.getId() + "</strong> is complete.<br>"
                + "Succeeded: " + succeeded + "<br>"
                + "Failed: " + failed + "<br><br>"
                + "Please <a href=\"" + escape(requestUrl) + "\">review the results</a>.<br><br>"
                + "Kind regards.";
        sendEmail(subject, body, creator, approver);
    }

    private void sendEmail(final String subject, final String body, final AppUser to, final AppUser cc) {
        final EmailDetail email = new EmailDetail(subject, body, to.getEmail(), displayName(to));
        if (hasEmail(cc) && !StringUtils.equalsIgnoreCase(StringUtils.trim(to.getEmail()), StringUtils.trim(cc.getEmail()))) {
            email.setCc(cc.getEmail().trim());
        }
        try {
            emailService.sendDefinedEmail(email);
            log.info("Bulk reschedule email sent to {} (cc {}) for request {}", to.getEmail(), email.getCc(),
                    subject);
        } catch (RuntimeException e) {
            log.error("Bulk reschedule email could not be sent to {}. Check SMTP configuration.", to.getEmail(), e);
        }
    }

    private AppUser loadUser(final AppUser user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        return appUserRepository.findById(user.getId()).orElse(user);
    }

    private String requestUrl(final Long executionId) {
        return StringUtils.removeEnd(StringUtils.defaultString(baseUrl), "/") + "/#/bulkreschedule/" + executionId;
    }

    private static boolean hasEmail(final AppUser user) {
        return user != null && StringUtils.isNotBlank(user.getEmail());
    }

    private static String displayName(final AppUser user) {
        return StringUtils.defaultIfBlank(user.getDisplayName(), user.getUsername());
    }

    private static String escape(final String value) {
        return HtmlUtils.htmlEscape(StringUtils.defaultString(value));
    }
}

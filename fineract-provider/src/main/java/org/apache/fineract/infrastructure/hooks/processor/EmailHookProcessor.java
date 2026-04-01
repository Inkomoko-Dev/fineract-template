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
package org.apache.fineract.infrastructure.hooks.processor;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.service.GmailBackedPlatformEmailService;
import org.apache.fineract.infrastructure.hooks.domain.Hook;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;


@Service
@Slf4j
@RequiredArgsConstructor
public class EmailHookProcessor implements HookProcessor {

    private final ClientRepositoryWrapper clientRepository;
    private final LoanRepository loanRepository;
    private final AppUserRepository appUserRepository;
    private final GmailBackedPlatformEmailService emailService;
    @Value("${CBS_ENVIRONMENT_LINK}")
    private String cbsEnvironmentLink;

    @Override
    public void process(Hook hook, String payload, String entityName, String actionName, FineractContext context) throws Exception {

        log.debug("Processing email hook {}", hook);

        if (cbsEnvironmentLink == null) {
            log.error("Failed to process email notification cbsEnvironmentLink is null");
            throw new Exception("cbsEnvironmentLink is null");
        }

        // Parse payload
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> payloadMap = new Gson().fromJson(payload, type);

        // Skip failed events
        if ("Exception".equals(payloadMap.get("status"))) {
            Map<String, Object> response = (Map<String, Object>) payloadMap.get("response");
            log.error("Loan action failed: {}", response.get("defaultUserMessage"));
            return;
        }

        Map<String, Object> response = (Map<String, Object>) payloadMap.get("response");
        Map<String, Object> request = (Map<String, Object>) payloadMap.get("request");

        // Extract loanId
        Long loanId = response != null && response.get("loanId") instanceof Number
                ? ((Number) response.get("loanId")).longValue()
                : null;

        if (loanId == null) {
            log.error("Loan ID is missing in the payload");
            return;
        }

        // Extract rejection info if any
        String rejectionDate = Optional.ofNullable(request)
                .map(r -> r.get("rejectedOnDate"))
                .map(Object::toString)
                .filter(s -> !s.isBlank())
                .orElseGet(() ->
                        LocalDate.now(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))
                );

        String rejectionReason = request != null ? (String) request.get("note") : null;

        // Fetch loan entity
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanId));

        // Prepare recipients and display names
        List<Map<String, String>> recipients = new ArrayList<>();
        AtomicReference<String> loanCreatorName = new AtomicReference<>(" ");
        AtomicReference<String> loanOfficerName = new AtomicReference<>(" ");

        // Loan creator
        if (loan.getCreatedBy().isPresent()) {
            Long creatorId = loan.getCreatedBy().get();
            Map<String, String> creatorMap = new HashMap<>();
            appUserRepository.findById(creatorId).ifPresentOrElse(
                    creator -> {

                        creatorMap.put("email" , creator.getEmail());
                        creatorMap.put("name", creator.getUsername());
                        recipients.add(creatorMap);
                        loanCreatorName.set(creator.getFirstname() + " " + creator.getLastname());
                    },
                    () -> log.warn("Loan creator not found for loan: {}", loanId)
            );
        }

        // Loan officer
        if (loan.getLoanOfficer() != null && loan.getLoanOfficer().getId() != null) {
            Long officerId = loan.getLoanOfficer().getId();
            Map<String, String> officerMap = new HashMap<>();
            appUserRepository.findById(officerId).ifPresentOrElse(
                    officer -> {
                        officerMap.put("email" , officer.getEmail());
                        officerMap.put("name", officer.getUsername());
                        recipients.add(officerMap);
                        loanOfficerName.set(officer.getFirstname() + " " + officer.getLastname());
                    },
                    () -> log.warn("Loan officer not found for loan: {}", loanId)
            );
        }

        // Fetch client
        Long clientId = response.get("clientId") instanceof Number
                ? ((Number) response.get("clientId")).longValue()
                : null;

        Client client = clientId != null ? clientRepository.findOneWithNotFoundDetection(clientId) : null;

        String clientFullName = client != null
                ? client.getFirstname() + " " + client.getLastname()
                : "Client";


        List<String> nameList = new ArrayList<>();
        if (!"N/A".equals(loanCreatorName.get())) {
            nameList.add(loanCreatorName.get());
        }
        if (!"N/A".equals(loanOfficerName.get())) {
            nameList.add(loanOfficerName.get());
        }

        String recipientNames = nameList.isEmpty() ? "Staff" : String.join(", ", nameList);


        // Prepare subject and message
        String subject;
        String message;

        String loanLink = String.format("%s/disbursement-request#/viewloanaccount/%s", cbsEnvironmentLink, loanId);


        switch (actionName) {

            case "DISBURSE":
                subject = String.format("Loan Disbursed – %s – %s", loanId, clientFullName);

                message = String.format(
                        "<p>Dear %s,</p>" +
                                "<p>The following loan has been successfully disbursed.</p>" +
                                "<h3>Loan Details</h3>" +
                                "<ul>" +
                                "<li>Loan ID / Reference: %s</li>" +
                                "<li>Client name: %s</li>" +
                                "<li>Disbursed amount: %s %s</li>" +
                                "<li>Disbursement date: %s</li>" +
                                "</ul>" +
                                "<p>You can open the loan record in the Core Banking System using the link below:<br>" +
                                "CBS Link: %s\n\n" +
                                "<p>This notification was sent to:<br>" +
                                "Loan creator: %s<br>" +
                                "Assigned loan officer: %s</p>" +
                                "<p>Please proceed with any required client communication, documentation, or follow‑up actions as per the standard process.</p>" +
                                "<p>Kind regards,<br>" +
                                "Core Banking System</p>",
                        recipientNames,
                        loanId,
                        clientFullName,
                        loan.getNetDisbursalAmount(),
                        loan.getCurrency().getCode(),
                        loan.getDisbursementDate(),
                        loanLink,
                        loanCreatorName.get(),
                        loanOfficerName.get()
                );
                break;

            case "REJECTDISBURSEMENT":
                subject = String.format("Loan Rejected – %s – %s", loanId, client != null ? client.getDisplayName() : "Client");

                 message = "<p>Dear " + recipientNames + ",</p>"
                    + "<p>The following loan application has been rejected / not approved.</p>"
                    + "<h3>Loan Details</h3>"
                    + "<ul>"
                    + "<li>Loan ID / Reference: " + loanId + "</li>"
                    + "<li>Client Name: " +  clientFullName + "</li>"
                    + "<li>Rejection Date: " + (rejectionDate != null ? rejectionDate : "N/A") + "</li>"
                    + "<li>Rejection Reason: " + (rejectionReason != null ? rejectionReason : "N/A") + "</li>"
                    + "</ul>"
                    + "<p>You can open the loan record in the Core Banking System using the link below:<br>" +
                    "CBS Link: " + loanLink +  "<br>" +
                    "<p>This notification was sent to:<br>"
                    + "Loan creator: " + loanCreatorName.get() + "<br>"
                    + "Assigned loan officer: " + loanOfficerName.get() + "</p>"
                    + "<p>Please review and take any necessary follow-up actions with the client or internal teams.</p>"
                    + "<p>Kind regards,<br>Core Banking System</p>";
                break;

            default:
                log.warn("Unsupported action {} for loan {}", actionName, loanId);
                return;
        }



        // Send email to each recipient
        for (Map<String, String> recipient : recipients) {
            String email = recipient.get("email");
            String contactName = recipient.getOrDefault("name", "Staff");
            EmailDetail emailDetail = new EmailDetail(subject, message, email, contactName);
            emailDetail.setAttachmentMimeType("text/html");

            log.info("Sending email to {} (contact: {}) | subject: {} | message:\n{}", email, contactName, subject, message);

            emailService.sendDefinedEmail(emailDetail);
        }
    }
}

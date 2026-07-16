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
package org.apache.fineract.infrastructure.campaigns.whatsapp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.campaigns.sms.exception.SmsRuntimeException;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignStatus;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppCampaignTriggerType;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaign;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppCampaignRepository;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.portfolio.businessevent.BusinessEventListener;
import org.apache.fineract.portfolio.businessevent.domain.client.ClientActivateBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.client.ClientRejectBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanApprovedBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanDisbursalBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanRejectedBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.transaction.LoanTransactionMakeRepaymentPostBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.savings.SavingsActivateBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.savings.SavingsRejectBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.savings.transaction.SavingsDepositBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.savings.transaction.SavingsWithdrawalBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.group.domain.Group;
import org.apache.fineract.portfolio.group.domain.GroupRepository;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.exception.InvalidLoanTypeException;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsAppCampaignDomainServiceImpl implements WhatsAppCampaignDomainService {

    private final WhatsAppCampaignRepository whatsAppCampaignRepository;
    private final CommunicationMessageRepository communicationMessageRepository;
    private final OfficeRepository officeRepository;
    private final BusinessEventNotifierService businessEventNotifierService;
    private final GroupRepository groupRepository;

    @PostConstruct
    public void addListeners() {
        businessEventNotifierService.addPostBusinessEventListener(LoanApprovedBusinessEvent.class, new SendWhatsAppOnLoanApproved());
        businessEventNotifierService.addPostBusinessEventListener(LoanDisbursalBusinessEvent.class, new SendWhatsAppOnLoanDisbursed());
        businessEventNotifierService.addPostBusinessEventListener(LoanRejectedBusinessEvent.class, new SendWhatsAppOnLoanRejected());
        businessEventNotifierService.addPostBusinessEventListener(LoanTransactionMakeRepaymentPostBusinessEvent.class,
                new SendWhatsAppOnLoanRepayment());
        businessEventNotifierService.addPostBusinessEventListener(ClientActivateBusinessEvent.class, new ClientActivatedListener());
        businessEventNotifierService.addPostBusinessEventListener(ClientRejectBusinessEvent.class, new ClientRejectedListener());
        businessEventNotifierService.addPostBusinessEventListener(SavingsActivateBusinessEvent.class, new SavingsAccountActivatedListener());
        businessEventNotifierService.addPostBusinessEventListener(SavingsRejectBusinessEvent.class, new SavingsAccountRejectedListener());
        businessEventNotifierService.addPostBusinessEventListener(SavingsDepositBusinessEvent.class,
                new DepositSavingsAccountTransactionListener());
        businessEventNotifierService.addPostBusinessEventListener(SavingsWithdrawalBusinessEvent.class,
                new NonDepositSavingsAccountTransactionListener());
    }

    private void notifyLoanOwner(final Loan loan, final String campaignParam) {
        final List<WhatsAppCampaign> campaigns = retrieveWhatsAppCampaigns(campaignParam);
        if (campaigns.isEmpty()) {
            return;
        }
        try {
            if (loan.hasInvalidLoanType()) {
                throw new InvalidLoanTypeException("Loan Type cannot be Invalid for the Triggered WhatsApp Campaign");
            }
            final Set<Client> clientSet = new HashSet<>();
            if (loan.isGroupLoan()) {
                final Group group = this.groupRepository.findById(loan.getGroupId()).orElse(null);
                if (group != null) {
                    clientSet.addAll(group.getClientMembers());
                }
            } else {
                clientSet.add(loan.client());
            }
            for (final WhatsAppCampaign campaign : campaigns) {
                if (campaign.isActive()) {
                    for (final Client client : clientSet) {
                        enqueueTriggeredMessage(client, campaign, buildLoanDataMap(loan, client));
                    }
                }
            }
        } catch (final RuntimeException e) {
            log.debug("Error enqueueing triggered WhatsApp for loan event {}", campaignParam, e);
        }
    }

    private void notifyClientActivated(final Client client) {
        final List<WhatsAppCampaign> campaigns = retrieveWhatsAppCampaigns("Client Activated");
        for (final WhatsAppCampaign campaign : campaigns) {
            enqueueTriggeredMessage(client, campaign, buildClientDataMap(client));
        }
    }

    private void notifyClientRejected(final Client client) {
        final List<WhatsAppCampaign> campaigns = retrieveWhatsAppCampaigns("Client Rejected");
        for (final WhatsAppCampaign campaign : campaigns) {
            enqueueTriggeredMessage(client, campaign, buildClientDataMap(client));
        }
    }

    private void notifySavingsAccountActivated(final SavingsAccount savingsAccount) {
        final List<WhatsAppCampaign> campaigns = retrieveWhatsAppCampaigns("Savings Activated");
        for (final WhatsAppCampaign campaign : campaigns) {
            enqueueTriggeredMessage(savingsAccount.getClient(), campaign, buildSavingsDataMap(savingsAccount, savingsAccount.getClient()));
        }
    }

    private void notifySavingsAccountRejected(final SavingsAccount savingsAccount) {
        final List<WhatsAppCampaign> campaigns = retrieveWhatsAppCampaigns("Savings Rejected");
        for (final WhatsAppCampaign campaign : campaigns) {
            enqueueTriggeredMessage(savingsAccount.getClient(), campaign, buildSavingsDataMap(savingsAccount, savingsAccount.getClient()));
        }
    }

    private void sendWhatsAppForLoanRepayment(final LoanTransaction loanTransaction) {
        final List<WhatsAppCampaign> campaigns = retrieveWhatsAppCampaigns("Loan Repayment");
        if (campaigns.isEmpty()) {
            return;
        }
        for (final WhatsAppCampaign campaign : campaigns) {
            try {
                final Loan loan = loanTransaction.getLoan();
                final Set<Client> groupClients = new HashSet<>();
                if (loan.hasInvalidLoanType()) {
                    throw new InvalidLoanTypeException("Loan Type cannot be Invalid for the Triggered WhatsApp Campaign");
                }
                if (loan.isGroupLoan()) {
                    final Group group = this.groupRepository.findById(loan.getGroupId()).orElse(null);
                    if (group != null) {
                        groupClients.addAll(group.getClientMembers());
                    }
                } else {
                    groupClients.add(loan.client());
                }
                final HashMap<String, String> campaignParams = new ObjectMapper().readValue(campaign.getParamValue(),
                        new TypeReference<HashMap<String, String>>() {});

                if (!groupClients.isEmpty()) {
                    for (final Client client : groupClients) {
                        final HashMap<String, Object> templateParams = processRepaymentDataForWhatsApp(loanTransaction, client);
                        validateCampaignParams(campaignParams, templateParams, client);
                        enqueueTriggeredMessage(client, campaign, templateParams);
                    }
                }
            } catch (final IOException e) {
                log.error("WhatsApp template params does not contain the key: ", e);
            } catch (final RuntimeException e) {
                log.debug("Client Office Id and WhatsApp Campaign Office id doesn't match ", e);
            }
        }
    }

    private void sendWhatsAppForSavingsTransaction(final SavingsAccountTransaction savingsTransaction, final boolean isDeposit) {
        final String campaignName = isDeposit ? "Savings Deposit" : "Savings Withdrawal";
        final List<WhatsAppCampaign> campaigns = retrieveWhatsAppCampaigns(campaignName);
        if (campaigns.isEmpty()) {
            return;
        }
        for (final WhatsAppCampaign campaign : campaigns) {
            try {
                final SavingsAccount savingsAccount = savingsTransaction.getSavingsAccount();
                final Client client = savingsAccount.getClient();
                final HashMap<String, String> campaignParams = new ObjectMapper().readValue(campaign.getParamValue(),
                        new TypeReference<HashMap<String, String>>() {});
                final HashMap<String, Object> templateParams = processSavingsTransactionDataForWhatsApp(savingsTransaction, client);
                validateCampaignParams(campaignParams, templateParams, client);
                enqueueTriggeredMessage(client, campaign, templateParams);
            } catch (final IOException e) {
                log.error("WhatsApp template params does not contain the key: ", e);
            } catch (final RuntimeException e) {
                log.debug("Client Office Id and WhatsApp Campaign Office id doesn't match ", e);
            }
        }
    }

    private List<WhatsAppCampaign> retrieveWhatsAppCampaigns(final String paramValue) {
        final Collection<WhatsAppCampaign> activeTriggered = this.whatsAppCampaignRepository.findByTriggerTypeAndStatus(
                WhatsAppCampaignTriggerType.TRIGGERED.getValue(), WhatsAppCampaignStatus.ACTIVE.getValue());
        final List<WhatsAppCampaign> matched = new ArrayList<>();
        if (activeTriggered == null) {
            return matched;
        }
        for (final WhatsAppCampaign campaign : activeTriggered) {
            final String campaignParamValue = campaign.getParamValue();
            if (campaignParamValue != null && campaignParamValue.contains(paramValue)) {
                matched.add(campaign);
            }
        }
        return matched;
    }

    private void validateCampaignParams(final HashMap<String, String> campaignParams, final HashMap<String, Object> templateParams,
            final Client client) {
        for (final String key : campaignParams.keySet()) {
            final String value = campaignParams.get(key);
            String spvalue = null;
            final boolean spkeycheck = templateParams.containsKey(key);
            if (spkeycheck) {
                spvalue = templateParams.get(key).toString();
            }
            if (spkeycheck && !(value.equals("-1") || spvalue.equals(value))) {
                if (key.equals("officeId")) {
                    final Office campaignOffice = this.officeRepository.findById(Long.valueOf(value)).orElse(null);
                    if (campaignOffice != null && campaignOffice.doesNotHaveAnOfficeInHierarchyWithId(client.getOffice().getId())) {
                        throw new SmsRuntimeException("error.msg.no.office", "Office not found for the id");
                    }
                } else {
                    throw new SmsRuntimeException("error.msg.no.id.attribute", "Office Id attribute is notfound");
                }
            }
        }
    }

    private void enqueueTriggeredMessage(final Client client, final WhatsAppCampaign campaign, final Map<String, Object> data) {
        try {
            final Object mobileNo = data.get("mobileNo");
            if (mobileNo == null || mobileNo.toString().isBlank()) {
                return;
            }
            final List<String> bodyValues = WhatsAppTemplateVariableMapper.toBodyValues(campaign.getBodyVariableMapping(), data);
            final String templateBodyValuesJson = new ObjectMapper().writeValueAsString(bodyValues);
            final String auditMessageBody = bodyValues.isEmpty() ? campaign.getMessage() : String.join("|", bodyValues);
            final CommunicationMessage message = CommunicationMessage.pendingOutboundTemplate(mobileNo.toString(), RecipientType.CLIENT,
                    client, null, campaign.getAtTemplateName(), campaign.getLanguageCode(), templateBodyValuesJson, auditMessageBody,
                    campaign.getId());
            this.communicationMessageRepository.save(message);
        } catch (final IOException e) {
            log.error("Error enqueueing triggered WhatsApp campaign message.", e);
        }
    }

    private HashMap<String, Object> buildClientDataMap(final Client client) {
        final HashMap<String, Object> params = new HashMap<>();
        params.put("id", client.getId());
        params.put("clientId", client.getId());
        params.put("firstname", client.getFirstname());
        params.put("middlename", client.getMiddlename());
        params.put("lastname", client.getLastname());
        params.put("FullName", client.getDisplayName());
        params.put("displayName", client.getDisplayName());
        params.put("mobileNo", client.mobileNo());
        params.put("officeId", client.getOffice().getId());
        if (client.getStaff() != null) {
            params.put("loanOfficerId", client.getStaff().getId());
        } else {
            params.put("loanOfficerId", -1);
        }
        return params;
    }

    private HashMap<String, Object> buildLoanDataMap(final Loan loan, final Client client) {
        final HashMap<String, Object> params = buildClientDataMap(client);
        params.put("loanId", loan.getId());
        params.put("LoanAccountId", loan.getAccountNumber());
        params.put("LoanAmount", loan.getPrincpal());
        params.put("LoanOutstanding", loan.getSummary().getTotalOutstanding());
        return params;
    }

    private HashMap<String, Object> buildSavingsDataMap(final SavingsAccount savingsAccount, final Client client) {
        final HashMap<String, Object> params = buildClientDataMap(client);
        params.put("savingsId", savingsAccount.getId());
        params.put("savingsAccountNo", savingsAccount.getAccountNumber());
        params.put("balance", savingsAccount.getWithdrawableBalance());
        return params;
    }

    private HashMap<String, Object> processRepaymentDataForWhatsApp(final LoanTransaction loanTransaction, final Client groupClient) {
        final HashMap<String, Object> templateParams = new HashMap<>();
        final Loan loan = loanTransaction.getLoan();
        final Client client;
        if (loan.isGroupLoan() && groupClient != null) {
            client = groupClient;
        } else if (loan.isIndividualLoan()) {
            client = loan.getClient();
        } else {
            throw new InvalidParameterException("");
        }

        final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM:d:yyyy");

        templateParams.put("id", loanTransaction.getLoan().getClientId());
        templateParams.put("firstname", client.getFirstname());
        templateParams.put("middlename", client.getMiddlename());
        templateParams.put("lastname", client.getLastname());
        templateParams.put("FullName", client.getDisplayName());
        templateParams.put("displayName", client.getDisplayName());
        templateParams.put("mobileNo", client.mobileNo());
        templateParams.put("LoanAmount", loan.getPrincpal());
        templateParams.put("LoanOutstanding", loanTransaction.getOutstandingLoanBalance());
        templateParams.put("loanId", loan.getId());
        templateParams.put("LoanAccountId", loan.getAccountNumber());
        templateParams.put("officeId", client.getOffice().getId());

        if (client.getStaff() != null) {
            templateParams.put("loanOfficerId", client.getStaff().getId());
        } else {
            templateParams.put("loanOfficerId", -1);
        }

        templateParams.put("repaymentAmount", loanTransaction.getAmount(loan.getCurrency()));
        templateParams.put("RepaymentDate", loanTransaction.getCreatedDateTime().toLocalDate().format(dateFormatter));
        templateParams.put("RepaymentTime", loanTransaction.getCreatedDateTime().toLocalTime().format(timeFormatter));

        if (loanTransaction.getPaymentDetail() != null) {
            templateParams.put("receiptNumber", loanTransaction.getPaymentDetail().getReceiptNumber());
        } else {
            templateParams.put("receiptNumber", -1);
        }
        return templateParams;
    }

    private HashMap<String, Object> processSavingsTransactionDataForWhatsApp(final SavingsAccountTransaction savingsAccountTransaction,
            final Client client) {
        final HashMap<String, Object> templateParams = new HashMap<>();
        final SavingsAccount savingsAccount = savingsAccountTransaction.getSavingsAccount();
        final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM:d:yyyy");
        templateParams.put("clientId", client.getId());
        templateParams.put("id", client.getId());
        templateParams.put("firstname", client.getFirstname());
        templateParams.put("middlename", client.getMiddlename());
        templateParams.put("lastname", client.getLastname());
        templateParams.put("FullName", client.getDisplayName());
        templateParams.put("displayName", client.getDisplayName());
        templateParams.put("mobileNo", client.mobileNo());
        templateParams.put("savingsId", savingsAccount.getId());
        templateParams.put("savingsAccountNo", savingsAccount.getAccountNumber());
        templateParams.put("withdrawAmount", savingsAccountTransaction.getAmount(savingsAccount.getCurrency()));
        templateParams.put("depositAmount", savingsAccountTransaction.getAmount(savingsAccount.getCurrency()));
        templateParams.put("balance", savingsAccount.getWithdrawableBalance());
        templateParams.put("officeId", client.getOffice().getId());
        templateParams.put("transactionDate", savingsAccountTransaction.getTransactionLocalDate().format(dateFormatter));
        templateParams.put("savingsTransactionId", savingsAccountTransaction.getId());

        if (client.getStaff() != null) {
            templateParams.put("loanOfficerId", client.getStaff().getId());
        } else {
            templateParams.put("loanOfficerId", -1);
        }

        if (savingsAccountTransaction.getPaymentDetail() != null) {
            templateParams.put("receiptNumber", savingsAccountTransaction.getPaymentDetail().getReceiptNumber());
        } else {
            templateParams.put("receiptNumber", -1);
        }
        return templateParams;
    }

    private class SendWhatsAppOnLoanApproved implements BusinessEventListener<LoanApprovedBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanApprovedBusinessEvent event) {
            notifyLoanOwner(event.get(), "Loan Approved");
        }
    }

    private class SendWhatsAppOnLoanDisbursed implements BusinessEventListener<LoanDisbursalBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanDisbursalBusinessEvent event) {
            notifyLoanOwner(event.get(), "Loan Disbursed");
        }
    }

    private class SendWhatsAppOnLoanRejected implements BusinessEventListener<LoanRejectedBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanRejectedBusinessEvent event) {
            notifyLoanOwner(event.get(), "Loan Rejected");
        }
    }

    private class SendWhatsAppOnLoanRepayment implements BusinessEventListener<LoanTransactionMakeRepaymentPostBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanTransactionMakeRepaymentPostBusinessEvent event) {
            sendWhatsAppForLoanRepayment(event.get());
        }
    }

    private class ClientActivatedListener implements BusinessEventListener<ClientActivateBusinessEvent> {

        @Override
        public void onBusinessEvent(final ClientActivateBusinessEvent event) {
            notifyClientActivated(event.get());
        }
    }

    private class ClientRejectedListener implements BusinessEventListener<ClientRejectBusinessEvent> {

        @Override
        public void onBusinessEvent(final ClientRejectBusinessEvent event) {
            notifyClientRejected(event.get());
        }
    }

    private class SavingsAccountActivatedListener implements BusinessEventListener<SavingsActivateBusinessEvent> {

        @Override
        public void onBusinessEvent(final SavingsActivateBusinessEvent event) {
            notifySavingsAccountActivated(event.get());
        }
    }

    private class SavingsAccountRejectedListener implements BusinessEventListener<SavingsRejectBusinessEvent> {

        @Override
        public void onBusinessEvent(final SavingsRejectBusinessEvent event) {
            notifySavingsAccountRejected(event.get());
        }
    }

    private class DepositSavingsAccountTransactionListener implements BusinessEventListener<SavingsDepositBusinessEvent> {

        @Override
        public void onBusinessEvent(final SavingsDepositBusinessEvent event) {
            sendWhatsAppForSavingsTransaction(event.get(), true);
        }
    }

    private class NonDepositSavingsAccountTransactionListener implements BusinessEventListener<SavingsWithdrawalBusinessEvent> {

        @Override
        public void onBusinessEvent(final SavingsWithdrawalBusinessEvent event) {
            sendWhatsAppForSavingsTransaction(event.get(), false);
        }
    }
}

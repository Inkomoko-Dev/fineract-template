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
package org.apache.fineract.infrastructure.whatsapp.interactive.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles MULTI_CLIENT / CLIENT_AND_STAFF shared-number prompts and follow-up replies.
 * Odoo staff sync for richer disambiguation remains deferred.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppSharedNumberDisambiguationService {

    private static final String KICKOFF_BODY = "__DISAMBIGUATED__";

    private final WhatsAppConversationSessionService sessionService;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final WhatsAppReplyService replyService;
    private final WhatsAppInboundConversationService inboundConversationService;
    private final WhatsAppStaffInboundConversationService staffInboundConversationService;
    private final WhatsAppInteractiveSettingsProvider settings;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final StaffRepositoryWrapper staffRepositoryWrapper;

    @Transactional
    public void handleAmbiguousInbound(final String phoneNumber, final String messageBody, final ResolvedRecipientData recipient) {
        final String language = settings.getDefaultLanguage();
        if (recipient.getRecipientType() == RecipientType.MULTI_CLIENT) {
            handleMultiClient(phoneNumber, messageBody, recipient, language);
            return;
        }
        if (recipient.getRecipientType() == RecipientType.CLIENT_AND_STAFF) {
            handleClientAndStaff(phoneNumber, messageBody, recipient, language);
        }
    }

    private void handleMultiClient(final String phoneNumber, final String messageBody, final ResolvedRecipientData recipient,
            final String language) {
        final WhatsAppConversationSession session = sessionService.startOrResume(phoneNumber, WhatsAppConversationType.CLIENT_SELF_SERVICE,
                null, null);
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        if (WhatsAppSessionContext.PENDING_DISAMBIGUATE_MULTI_CLIENT.equals(context.getPendingAction())) {
            final Client chosen = resolveClientChoice(messageBody.trim(), recipient.getClientIds());
            if (chosen == null) {
                replyService.sendTransactionalReply(phoneNumber, RecipientType.MULTI_CLIENT, null, null,
                        WhatsAppInteractiveMessages.sharedNumberMultiClientPrompt(language));
                return;
            }
            clearDisambiguation(context);
            session.setClient(chosen);
            session.setSessionContext(contextSerializer.toJson(context));
            session.setSessionStatus(WhatsAppSessionStatus.LANGUAGE_SELECTION);
            session.setCurrentMenuKey("LANGUAGE");
            sessionService.save(session);
            inboundConversationService.handleInbound(phoneNumber, KICKOFF_BODY, RecipientType.CLIENT, chosen, null);
            return;
        }
        beginMultiClientPrompt(session, context, recipient.getClientIds(), phoneNumber, language);
    }

    private void handleClientAndStaff(final String phoneNumber, final String messageBody, final ResolvedRecipientData recipient,
            final String language) {
        final WhatsAppConversationSession session = sessionService.startOrResume(phoneNumber, WhatsAppConversationType.CLIENT_SELF_SERVICE,
                null, null);
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        if (WhatsAppSessionContext.PENDING_DISAMBIGUATE_CLIENT_AND_STAFF.equals(context.getPendingAction())) {
            final String choice = messageBody.trim();
            if ("1".equals(choice) && recipient.getClientId() != null) {
                final Client client = clientRepositoryWrapper.findOneWithNotFoundDetection(recipient.getClientId());
                clearDisambiguation(context);
                session.setClient(client);
                session.setSessionContext(contextSerializer.toJson(context));
                session.setSessionStatus(WhatsAppSessionStatus.LANGUAGE_SELECTION);
                session.setCurrentMenuKey("LANGUAGE");
                sessionService.save(session);
                inboundConversationService.handleInbound(phoneNumber, KICKOFF_BODY, RecipientType.CLIENT, client, null);
                return;
            }
            if ("2".equals(choice) && recipient.getStaffId() != null) {
                final Staff staff = staffRepositoryWrapper.findOneWithNotFoundDetection(recipient.getStaffId());
                clearDisambiguation(context);
                session.setSessionStatus(WhatsAppSessionStatus.CLOSED);
                session.setSessionContext(contextSerializer.toJson(context));
                sessionService.save(session);
                staffInboundConversationService.handleInbound(phoneNumber, KICKOFF_BODY, RecipientType.STAFF, staff);
                return;
            }
            replyService.sendTransactionalReply(phoneNumber, RecipientType.CLIENT_AND_STAFF, null, null,
                    WhatsAppInteractiveMessages.sharedNumberClientAndStaffPrompt(language));
            return;
        }
        beginClientAndStaffPrompt(session, context, recipient, phoneNumber, language);
    }

    private void beginMultiClientPrompt(final WhatsAppConversationSession session, final WhatsAppSessionContext context,
            final List<Long> clientIds, final String phoneNumber, final String language) {
        context.setPendingAction(WhatsAppSessionContext.PENDING_DISAMBIGUATE_MULTI_CLIENT);
        context.setCandidateClientIds(clientIds == null ? new ArrayList<>() : new ArrayList<>(clientIds));
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.AWAITING_INPUT);
        sessionService.save(session);
        replyService.sendTransactionalReply(phoneNumber, RecipientType.MULTI_CLIENT, null, null,
                WhatsAppInteractiveMessages.sharedNumberMultiClientPrompt(language));
    }

    private void beginClientAndStaffPrompt(final WhatsAppConversationSession session, final WhatsAppSessionContext context,
            final ResolvedRecipientData recipient, final String phoneNumber, final String language) {
        context.setPendingAction(WhatsAppSessionContext.PENDING_DISAMBIGUATE_CLIENT_AND_STAFF);
        context.setCandidateClientId(recipient.getClientId());
        context.setCandidateStaffId(recipient.getStaffId());
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(WhatsAppSessionStatus.AWAITING_INPUT);
        sessionService.save(session);
        replyService.sendTransactionalReply(phoneNumber, RecipientType.CLIENT_AND_STAFF, null, null,
                WhatsAppInteractiveMessages.sharedNumberClientAndStaffPrompt(language));
    }

    private Client resolveClientChoice(final String accountOrInput, final List<Long> allowedClientIds) {
        if (StringUtils.isBlank(accountOrInput) || allowedClientIds == null || allowedClientIds.isEmpty()) {
            return null;
        }
        try {
            final Client client = clientRepositoryWrapper.getClientByAccountNumber(accountOrInput);
            if (client != null && allowedClientIds.contains(client.getId())) {
                return client;
            }
        } catch (final ClientNotFoundException ex) {
            log.debug("Shared-number multi-client choice did not match an account: {}", accountOrInput);
        } catch (final RuntimeException ex) {
            log.warn("Failed resolving shared-number multi-client choice {}", accountOrInput, ex);
        }
        return null;
    }

    private void clearDisambiguation(final WhatsAppSessionContext context) {
        context.setPendingAction(null);
        context.setCandidateClientIds(new ArrayList<>());
        context.setCandidateClientId(null);
        context.setCandidateStaffId(null);
    }
}

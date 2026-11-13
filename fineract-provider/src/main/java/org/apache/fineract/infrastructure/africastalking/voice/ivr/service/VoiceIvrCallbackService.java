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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.service;

import java.util.ArrayList;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingPayloadParser;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.africastalking.service.RecipientResolutionService;
import org.apache.fineract.infrastructure.africastalking.service.VoiceXmlBuilder;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrMenuActionType;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuDefinition;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuDefinitionRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOption;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrMenuOptionRepository;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppAuthStep;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppSessionContextSerializer;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceIvrCallbackService {

    private static final String SELECT_ROLE_STEP = "SELECT_ROLE";

    private final AfricasTalkingProperties properties;
    private final VoiceIvrSessionService sessionService;
    private final VoiceIvrMenuRenderer menuRenderer;
    private final VoiceIvrMenuOptionRepository menuOptionRepository;
    private final VoiceIvrMenuDefinitionRepository menuDefinitionRepository;
    private final VoiceIvrCallerIdentificationService callerIdentificationService;
    private final VoiceIvrClientAuthService clientAuthService;
    private final VoiceIvrLoanSelfServiceGate loanSelfServiceGate;
    private final VoiceAgentRoutingService agentRoutingService;
    private final VoiceCallbackRequestService callbackRequestService;
    private final VoiceVoicemailService voicemailService;
    private final VoiceBusinessHoursService businessHoursService;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final RecipientResolutionService recipientResolutionService;
    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final StaffRepositoryWrapper staffRepositoryWrapper;
    private final VoiceRecordingConsentService recordingConsentService;
    private final VoiceIvrDialTargetResolver dialTargetResolver;

    @Transactional
    public String handleInbound(final String rawPayload) {
        final Map<String, String> values = AfricasTalkingPayloadParser.toMap(rawPayload);
        final String sessionId = AfricasTalkingPayloadParser.firstNonBlank(values, "sessionId", "callSessionId");
        if (StringUtils.isBlank(sessionId)) {
            return VoiceXmlBuilder.buildInvalidSelection();
        }
        final String callerNumber = normalizePhone(AfricasTalkingPayloadParser.firstNonBlank(values, "callerNumber", "from"));
        final ResolvedRecipientData recipient = recipientResolutionService.resolve(callerNumber);
        final Client client = resolveInitialClient(recipient);
        final Staff staff = resolveInitialStaff(recipient);
        final VoiceIvrSession session = sessionService.startOrResume(sessionId, callerNumber, client, staff);
        refreshCallerIdentity(session, client, staff);
        storeRoleCandidatesIfNeeded(session, recipient);
        storeMultiClientCandidatesIfNeeded(session, recipient);
        final String dtmfDigits = AfricasTalkingPayloadParser.firstNonBlank(values, "dtmfDigits", "digits");
        if (!businessHoursService.isWithinBusinessHours()) {
            return handleAfterHours(session, dtmfDigits);
        }
        if (StringUtils.isBlank(dtmfDigits)) {
            if (session.getSessionStatus() == VoiceIvrSessionStatus.ACTIVE) {
                return handleInvalidOrEmptyInput(session, true);
            }
            return presentCurrentStep(session);
        }
        if (isAwaitingMultiClientDisambiguation(session)) {
            return processMultiClientAccountSelection(session, dtmfDigits.trim());
        }
        if (requiresLanguageSelection(session)) {
            return processLanguageSelection(session, dtmfDigits.trim());
        }
        if (isAwaitingRoleSelection(session)) {
            return processRoleSelection(session, dtmfDigits.trim());
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.AUTHENTICATING) {
            return clientAuthService.handleAuthInput(session, dtmfDigits.trim());
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.RECORDING_CONSENT) {
            return recordingConsentService.handleConsentInput(session, dtmfDigits.trim());
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.AWAITING_INPUT) {
            return handleAwaitingInput(session, dtmfDigits.trim());
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.QUEUED) {
            return agentRoutingService.advanceQueuedCall(session, resolveLanguage(session));
        }
        return processSelection(session, dtmfDigits.trim());
    }

    private Client resolveInitialClient(final ResolvedRecipientData recipient) {
        if (recipient.getRecipientType() == RecipientType.CLIENT && recipient.getClientId() != null) {
            return clientRepositoryWrapper.findOneWithNotFoundDetection(recipient.getClientId());
        }
        // MULTI_CLIENT / CLIENT_AND_STAFF: do not auto-bind until disambiguated.
        return null;
    }

    private Staff resolveInitialStaff(final ResolvedRecipientData recipient) {
        if (recipient.getRecipientType() == RecipientType.STAFF && recipient.getStaffId() != null) {
            return staffRepositoryWrapper.findOneWithNotFoundDetection(recipient.getStaffId());
        }
        return null;
    }

    private void storeMultiClientCandidatesIfNeeded(final VoiceIvrSession session, final ResolvedRecipientData recipient) {
        if (!recipient.isAmbiguousClients()) {
            return;
        }
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        if (WhatsAppSessionContext.PENDING_DISAMBIGUATE_MULTI_CLIENT.equals(context.getPendingAction())) {
            return;
        }
        context.setPendingAction(WhatsAppSessionContext.PENDING_DISAMBIGUATE_MULTI_CLIENT);
        context.setCandidateClientIds(
                recipient.getClientIds() == null ? new ArrayList<>() : new ArrayList<>(recipient.getClientIds()));
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(VoiceIvrSessionStatus.AWAITING_INPUT);
        sessionService.save(session);
    }

    private boolean isAwaitingMultiClientDisambiguation(final VoiceIvrSession session) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        return WhatsAppSessionContext.PENDING_DISAMBIGUATE_MULTI_CLIENT.equals(context.getPendingAction());
    }

    private String processMultiClientAccountSelection(final VoiceIvrSession session, final String input) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final String language = resolveLanguage(session);
        final String accountInput = StringUtils.stripEnd(StringUtils.trimToEmpty(input), "#");
        Client chosen = null;
        if (StringUtils.isNotBlank(accountInput)) {
            try {
                final Client client = clientRepositoryWrapper.getClientByAccountNumber(accountInput);
                final var allowed = context.getCandidateClientIds();
                if (client != null && allowed != null && allowed.contains(client.getId())) {
                    chosen = client;
                }
            } catch (final ClientNotFoundException ignored) {
                chosen = null;
            }
        }
        if (chosen == null) {
            return handleInvalidInput(session, VoiceIvrMessages.multiClientAccountInvalid(language));
        }
        clearInvalidAttempts(session);
        session.setClient(chosen);
        clearMultiClientDisambiguation(session, context);
        if (requiresLanguageSelection(session)) {
            return presentMenu(session, properties.getVoiceIvr().getLanguageMenuKey(),
                    properties.getVoiceIvr().getLanguagePickerLanguage(), false);
        }
        return presentMenu(session, properties.getVoiceIvr().getMainMenuKey(), language, true);
    }

    private void clearMultiClientDisambiguation(final VoiceIvrSession session, final WhatsAppSessionContext context) {
        context.setPendingAction(null);
        context.setCandidateClientIds(new ArrayList<>());
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        sessionService.save(session);
    }

    private void storeRoleCandidatesIfNeeded(final VoiceIvrSession session, final ResolvedRecipientData recipient) {
        if (!recipient.requiresRoleSelection()) {
            return;
        }
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        if (SELECT_ROLE_STEP.equals(context.getAuthStep())) {
            return;
        }
        context.setAuthStep(SELECT_ROLE_STEP);
        context.setCandidateClientId(recipient.getClientId());
        context.setPendingAction(recipient.getStaffId() != null ? String.valueOf(recipient.getStaffId()) : null);
        session.setSessionContext(contextSerializer.toJson(context));
        sessionService.save(session);
    }

    private boolean isAwaitingRoleSelection(final VoiceIvrSession session) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        return SELECT_ROLE_STEP.equals(context.getAuthStep());
    }

    private String processRoleSelection(final VoiceIvrSession session, final String dtmfDigits) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final String language = resolveLanguage(session);
        if ("1".equals(dtmfDigits) && context.getCandidateClientId() != null) {
            final Client client = clientRepositoryWrapper.findOneWithNotFoundDetection(context.getCandidateClientId());
            session.setClient(client);
            session.setStaff(null);
            clearInvalidAttempts(session);
            clearRoleSelection(session, context);
            return presentMenu(session, properties.getVoiceIvr().getMainMenuKey(), language, true);
        }
        if ("2".equals(dtmfDigits) && StringUtils.isNotBlank(context.getPendingAction())) {
            final Staff staff = staffRepositoryWrapper.findOneWithNotFoundDetection(Long.parseLong(context.getPendingAction()));
            session.setStaff(staff);
            session.setClient(null);
            clearInvalidAttempts(session);
            clearRoleSelection(session, context);
            return presentMenu(session, properties.getVoiceIvr().getMainMenuKey(), language, true);
        }
        return handleInvalidInput(session, VoiceIvrMessages.roleSelectPrompt(language));
    }

    private void clearRoleSelection(final VoiceIvrSession session, final WhatsAppSessionContext context) {
        context.setAuthStep(null);
        context.setCandidateClientId(null);
        context.setPendingAction(null);
        session.setSessionContext(contextSerializer.toJson(context));
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        sessionService.save(session);
    }

    private String handleAfterHours(final VoiceIvrSession session, final String dtmfDigits) {
        if (requiresLanguageSelection(session)) {
            if (StringUtils.isBlank(dtmfDigits)) {
                return presentMenu(session, properties.getVoiceIvr().getLanguageMenuKey(), properties.getVoiceIvr().getLanguagePickerLanguage(),
                        false);
            }
            final String languagePickerPrompt = menuRenderer.buildMenuPrompt(properties.getVoiceIvr().getLanguageMenuKey(),
                    properties.getVoiceIvr().getLanguagePickerLanguage());
            if (!NumberUtils.isDigits(dtmfDigits.trim())) {
                return handleInvalidInput(session, languagePickerPrompt);
            }
            final String languageCode = resolveLanguageOption(dtmfDigits.trim());
            if (StringUtils.isBlank(languageCode)) {
                return handleInvalidInput(session, languagePickerPrompt);
            }
            clearInvalidAttempts(session);
            session.setLanguageCode(languageCode);
            session.setSessionStatus(VoiceIvrSessionStatus.AFTER_HOURS);
            sessionService.save(session);
            return presentAfterHoursMenu(session);
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.VOICEMAIL) {
            return voicemailService.completeSession(session, resolveLanguage(session));
        }
        if (StringUtils.isBlank(dtmfDigits)) {
            return presentAfterHoursMenu(session);
        }
        final String language = resolveLanguage(session);
        if ("1".equals(dtmfDigits.trim())) {
            clearInvalidAttempts(session);
            return callbackRequestService.requestCallback(session, "AFTER_HOURS", language);
        }
        if ("2".equals(dtmfDigits.trim())) {
            clearInvalidAttempts(session);
            return voicemailService.beginRecording(session, "AFTER_HOURS", language);
        }
        return handleInvalidInput(session, VoiceIvrMessages.afterHoursMenu(language));
    }

    private String presentAfterHoursMenu(final VoiceIvrSession session) {
        session.setSessionStatus(VoiceIvrSessionStatus.AFTER_HOURS);
        sessionService.save(session);
        return VoiceXmlBuilder.buildAfterHoursMenu(VoiceIvrMessages.afterHoursMenu(resolveLanguage(session)));
    }

    private String presentCurrentStep(final VoiceIvrSession session) {
        if (isAwaitingMultiClientDisambiguation(session)) {
            return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.multiClientAccountPrompt(resolveLanguage(session)));
        }
        if (requiresLanguageSelection(session)) {
            return presentMenu(session, properties.getVoiceIvr().getLanguageMenuKey(), properties.getVoiceIvr().getLanguagePickerLanguage(),
                    false);
        }
        if (isAwaitingRoleSelection(session)) {
            return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.roleSelectPrompt(resolveLanguage(session)));
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.AUTHENTICATING) {
            return VoiceXmlBuilder.buildCollectInput(clientAuthService.getAuthReprompt(session));
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.RECORDING_CONSENT) {
            return recordingConsentService.reprompt(session);
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.AWAITING_INPUT) {
            return presentAwaitingInputPrompt(session);
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.QUEUED) {
            return agentRoutingService.advanceQueuedCall(session, resolveLanguage(session));
        }
        return presentMenu(session, resolveActiveMenuKey(session), resolveLanguage(session), true);
    }

    private String presentAwaitingInputPrompt(final VoiceIvrSession session) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        if (WhatsAppAuthStep.SELECT_LOAN.name().equals(context.getAuthStep())) {
            final Long clientId = context.getCandidateClientId() != null ? context.getCandidateClientId()
                    : session.getClient() != null ? session.getClient().getId() : null;
            if (clientId != null) {
                final String language = resolveLanguage(session);
                final String menu = loanSelfServiceGate.buildLoanSelectionPrompt(clientId, language);
                return VoiceXmlBuilder.buildCollectInput(menu);
            }
        }
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        sessionService.save(session);
        return presentMenu(session, resolveActiveMenuKey(session), resolveLanguage(session), true);
    }

    private String handleAwaitingInput(final VoiceIvrSession session, final String input) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        if (WhatsAppSessionContext.PENDING_DISAMBIGUATE_MULTI_CLIENT.equals(context.getPendingAction())) {
            return processMultiClientAccountSelection(session, input);
        }
        if (WhatsAppAuthStep.SELECT_LOAN.name().equals(context.getAuthStep())) {
            return loanSelfServiceGate.handleLoanSelection(session, input);
        }
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        sessionService.save(session);
        return presentMenu(session, resolveActiveMenuKey(session), resolveLanguage(session), true);
    }

    private String presentMenu(final VoiceIvrSession session, final String menuKey, final String menuLanguage,
            final boolean includeCallerGreeting) {
        session.setCurrentMenuKey(menuKey);
        clearInvalidAttempts(session);
        sessionService.save(session);
        final String menuPrompt = menuRenderer.buildMenuPrompt(menuKey, menuLanguage);
        if (StringUtils.isBlank(menuPrompt)) {
            return VoiceXmlBuilder.buildInvalidSelection();
        }
        if (!includeCallerGreeting) {
            return VoiceXmlBuilder.buildMenuPrompt(menuPrompt);
        }
        final String greeting = callerIdentificationService.buildGreeting(session, resolveLanguage(session));
        final String prompt = StringUtils.isNotBlank(greeting) ? greeting + " " + menuPrompt : menuPrompt;
        return VoiceXmlBuilder.buildMenuPrompt(prompt);
    }

    private String processLanguageSelection(final VoiceIvrSession session, final String dtmfDigits) {
        if (!NumberUtils.isDigits(dtmfDigits)) {
            return handleInvalidInput(session, menuRenderer.buildMenuPrompt(properties.getVoiceIvr().getLanguageMenuKey(),
                    properties.getVoiceIvr().getLanguagePickerLanguage()));
        }
        final String languageCode = resolveLanguageOption(dtmfDigits);
        if (StringUtils.isBlank(languageCode)) {
            return handleInvalidInput(session, menuRenderer.buildMenuPrompt(properties.getVoiceIvr().getLanguageMenuKey(),
                    properties.getVoiceIvr().getLanguagePickerLanguage()));
        }
        clearInvalidAttempts(session);
        return applyLanguageSelection(session, languageCode);
    }

    private String resolveLanguageOption(final String dtmfDigits) {
        final String pickerLanguage = properties.getVoiceIvr().getLanguagePickerLanguage();
        final VoiceIvrMenuOption option = menuOptionRepository
                .findByMenuKeyAndLanguageCodeAndOptionDigitAndEnabledTrue(properties.getVoiceIvr().getLanguageMenuKey(), pickerLanguage,
                        Integer.parseInt(dtmfDigits))
                .orElse(null);
        if (option == null || option.getActionType() != VoiceIvrMenuActionType.LANGUAGE_SELECT
                || StringUtils.isBlank(option.getActionTarget())) {
            return null;
        }
        return option.getActionTarget();
    }

    private String applyLanguageSelection(final VoiceIvrSession session, final String languageCode) {
        if (StringUtils.isBlank(languageCode)) {
            return VoiceXmlBuilder.buildInvalidSelection();
        }
        session.setLanguageCode(languageCode);
        session.setSessionStatus(VoiceIvrSessionStatus.ACTIVE);
        session.setCurrentMenuKey(properties.getVoiceIvr().getMainMenuKey());
        session.setParentMenuKey(null);
        sessionService.save(session);
        if (isAwaitingRoleSelection(session)) {
            return VoiceXmlBuilder.buildCollectInput(VoiceIvrMessages.roleSelectPrompt(languageCode));
        }
        return presentMenu(session, properties.getVoiceIvr().getMainMenuKey(), languageCode, true);
    }

    private String processSelection(final VoiceIvrSession session, final String dtmfDigits) {
        if (!NumberUtils.isDigits(dtmfDigits)) {
            return handleInvalidOrEmptyInput(session, false);
        }
        final String language = resolveLanguage(session);
        final VoiceIvrMenuOption option = menuOptionRepository
                .findByMenuKeyAndLanguageCodeAndOptionDigitAndEnabledTrue(session.getCurrentMenuKey(), language,
                        Integer.parseInt(dtmfDigits))
                .orElse(null);
        if (option == null) {
            return handleInvalidOrEmptyInput(session, false);
        }
        clearInvalidAttempts(session);
        return dispatchAction(session, option, language);
    }

    private String handleInvalidOrEmptyInput(final VoiceIvrSession session, final boolean emptyInput) {
        final String language = resolveLanguage(session);
        final String menuPrompt = menuRenderer.buildMenuPrompt(resolveActiveMenuKey(session), language);
        return handleInvalidInput(session, menuPrompt);
    }

    private String handleInvalidInput(final VoiceIvrSession session, final String retryPrompt) {
        final int attempts = incrementInvalidAttempts(session);
        final String language = resolveLanguage(session);
        final int maxAttempts = Math.max(1, properties.getVoiceIvr().getMaxInvalidDtmfAttempts());
        if (attempts >= maxAttempts) {
            return transferOrHangupAfterInvalidAttempts(session, language);
        }
        final String retry = VoiceIvrMessages.invalidSelectionRetry(language);
        if (StringUtils.isBlank(retryPrompt)) {
            return VoiceXmlBuilder.buildCollectInput(retry);
        }
        return VoiceXmlBuilder.buildSayWithMenu(retry, retryPrompt);
    }

    private String transferOrHangupAfterInvalidAttempts(final VoiceIvrSession session, final String language) {
        final String supportNumber = dialTargetResolver.resolve("SUPPORT", session);
        sessionService.markClosed(session);
        if (StringUtils.isNotBlank(supportNumber)) {
            return VoiceXmlBuilder.buildConnectingDial(VoiceIvrMessages.transferAttemptsExhausted(language), supportNumber, false,
                    VoiceIvrMessages.agentUnavailableFallback(language));
        }
        return VoiceXmlBuilder.buildHangup(VoiceIvrMessages.transferAttemptsExhausted(language));
    }

    private int incrementInvalidAttempts(final VoiceIvrSession session) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        final int next = (context.getInvalidDtmfAttempts() == null ? 0 : context.getInvalidDtmfAttempts()) + 1;
        context.setInvalidDtmfAttempts(next);
        session.setSessionContext(contextSerializer.toJson(context));
        sessionService.save(session);
        return next;
    }

    private void clearInvalidAttempts(final VoiceIvrSession session) {
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        if (context.getInvalidDtmfAttempts() == null || context.getInvalidDtmfAttempts() == 0) {
            return;
        }
        context.setInvalidDtmfAttempts(0);
        session.setSessionContext(contextSerializer.toJson(context));
        sessionService.save(session);
    }

    private String dispatchAction(final VoiceIvrSession session, final VoiceIvrMenuOption option, final String language) {
        return switch (option.getActionType()) {
            case SUBMENU -> navigateToSubmenu(session, option);
            case DIAL -> agentRoutingService.transferToDepartment(session, option, language);
            case QUEUE -> agentRoutingService.joinQueue(session, option, language);
            case CALLBACK_REQUEST -> callbackRequestService.requestCallback(session, option.getActionTarget(), language);
            case RECORD_VOICEMAIL -> voicemailService.beginRecording(session, option.getActionTarget(), language);
            case SAY -> VoiceXmlBuilder.buildSay(StringUtils.defaultString(option.getActionTarget()));
            case LANGUAGE_SELECT -> applyLanguageSelection(session, option.getActionTarget());
            case LOAN_SERVICE -> loanSelfServiceGate.beginLoanService(session, option.getActionTarget());
            case REPEAT -> presentMenu(session, resolveActiveMenuKey(session), language, false);
            case PREVIOUS -> navigateToPreviousMenu(session, language);
            case HANGUP -> {
                sessionService.markClosed(session);
                yield VoiceXmlBuilder.buildHangup(VoiceIvrMessages.goodbye(language));
            }
        };
    }

    private String navigateToSubmenu(final VoiceIvrSession session, final VoiceIvrMenuOption option) {
        session.setParentMenuKey(session.getCurrentMenuKey());
        session.setCurrentMenuKey(option.getActionTarget());
        sessionService.save(session);
        return presentMenu(session, option.getActionTarget(), resolveLanguage(session), true);
    }

    private String navigateToPreviousMenu(final VoiceIvrSession session, final String language) {
        String parent = session.getParentMenuKey();
        if (StringUtils.isBlank(parent)) {
            parent = menuDefinitionRepository
                    .findByMenuKeyAndLanguageCodeAndEnabledTrue(resolveActiveMenuKey(session), language)
                    .map(VoiceIvrMenuDefinition::getParentMenuKey).orElse(null);
        }
        if (StringUtils.isBlank(parent)) {
            parent = properties.getVoiceIvr().getMainMenuKey();
        }
        session.setParentMenuKey(null);
        session.setCurrentMenuKey(parent);
        sessionService.save(session);
        return presentMenu(session, parent, language, true);
    }

    private void refreshCallerIdentity(final VoiceIvrSession session, final Client client, final Staff staff) {
        if (session.getClient() == null && client != null) {
            session.setClient(client);
        }
        if (session.getStaff() == null && staff != null) {
            session.setStaff(staff);
        }
    }

    private boolean requiresLanguageSelection(final VoiceIvrSession session) {
        return session.getSessionStatus() == VoiceIvrSessionStatus.LANGUAGE_SELECTION || StringUtils.isBlank(session.getLanguageCode());
    }

    private String resolveActiveMenuKey(final VoiceIvrSession session) {
        return StringUtils.defaultIfBlank(session.getCurrentMenuKey(), properties.getVoiceIvr().getMainMenuKey());
    }

    private String resolveLanguage(final VoiceIvrSession session) {
        return StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getVoiceIvr().getDefaultLanguage());
    }

    private String normalizePhone(final String phoneNumber) {
        return phoneNumberNormalizer.normalize(phoneNumber);
    }
}

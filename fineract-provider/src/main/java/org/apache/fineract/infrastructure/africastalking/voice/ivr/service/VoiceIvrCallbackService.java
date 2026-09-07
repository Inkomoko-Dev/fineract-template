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

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.infrastructure.africastalking.service.AfricasTalkingPayloadParser;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.africastalking.service.RecipientResolutionService;
import org.apache.fineract.infrastructure.africastalking.service.VoiceXmlBuilder;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrMenuActionType;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.constants.VoiceIvrSessionStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceIvrCallbackService {

    private final AfricasTalkingProperties properties;
    private final VoiceIvrSessionService sessionService;
    private final VoiceIvrMenuRenderer menuRenderer;
    private final VoiceIvrMenuOptionRepository menuOptionRepository;
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

    @Transactional
    public String handleInbound(final String rawPayload) {
        final Map<String, String> values = AfricasTalkingPayloadParser.toMap(rawPayload);
        final String sessionId = AfricasTalkingPayloadParser.firstNonBlank(values, "sessionId", "callSessionId");
        if (StringUtils.isBlank(sessionId)) {
            return VoiceXmlBuilder.buildInvalidSelection();
        }
        final String callerNumber = normalizePhone(AfricasTalkingPayloadParser.firstNonBlank(values, "callerNumber", "from"));
        final ResolvedRecipientData recipient = recipientResolutionService.resolve(callerNumber);
        final Client client = recipient.getClientId() != null
                ? clientRepositoryWrapper.findOneWithNotFoundDetection(recipient.getClientId()) : null;
        final Staff staff = recipient.getStaffId() != null
                ? staffRepositoryWrapper.findOneWithNotFoundDetection(recipient.getStaffId()) : null;
        final VoiceIvrSession session = sessionService.startOrResume(sessionId, callerNumber, client, staff);
        refreshCallerIdentity(session, client, staff);
        final String dtmfDigits = AfricasTalkingPayloadParser.firstNonBlank(values, "dtmfDigits", "digits");
        if (!businessHoursService.isWithinBusinessHours()) {
            return handleAfterHours(session, dtmfDigits);
        }
        if (StringUtils.isBlank(dtmfDigits)) {
            return presentCurrentStep(session);
        }
        if (requiresLanguageSelection(session)) {
            return processLanguageSelection(session, dtmfDigits.trim());
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.AUTHENTICATING) {
            return clientAuthService.handleAuthInput(session, dtmfDigits.trim());
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.AWAITING_INPUT) {
            return handleAwaitingInput(session, dtmfDigits.trim());
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.QUEUED) {
            return agentRoutingService.advanceQueuedCall(session, resolveLanguage(session));
        }
        return processSelection(session, dtmfDigits.trim());
    }

    private String handleAfterHours(final VoiceIvrSession session, final String dtmfDigits) {
        if (requiresLanguageSelection(session)) {
            if (StringUtils.isBlank(dtmfDigits)) {
                return presentMenu(session, properties.getVoiceIvr().getLanguageMenuKey(), properties.getVoiceIvr().getLanguagePickerLanguage(),
                        false);
            }
            if (!NumberUtils.isDigits(dtmfDigits.trim())) {
                return VoiceXmlBuilder.buildInvalidSelection();
            }
            final String languageCode = resolveLanguageOption(dtmfDigits.trim());
            if (StringUtils.isBlank(languageCode)) {
                return VoiceXmlBuilder.buildInvalidSelection();
            }
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
            return callbackRequestService.requestCallback(session, "AFTER_HOURS", language);
        }
        if ("2".equals(dtmfDigits.trim())) {
            return voicemailService.beginRecording(session, "AFTER_HOURS", language);
        }
        return VoiceXmlBuilder.buildInvalidSelection();
    }

    private String presentAfterHoursMenu(final VoiceIvrSession session) {
        session.setSessionStatus(VoiceIvrSessionStatus.AFTER_HOURS);
        sessionService.save(session);
        return VoiceXmlBuilder.buildAfterHoursMenu(VoiceIvrMessages.afterHoursMenu(resolveLanguage(session)));
    }

    private String presentCurrentStep(final VoiceIvrSession session) {
        if (requiresLanguageSelection(session)) {
            return presentMenu(session, properties.getVoiceIvr().getLanguageMenuKey(), properties.getVoiceIvr().getLanguagePickerLanguage(),
                    false);
        }
        if (session.getSessionStatus() == VoiceIvrSessionStatus.AUTHENTICATING) {
            return VoiceXmlBuilder.buildCollectInput(clientAuthService.getAuthReprompt(session));
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
            return VoiceXmlBuilder.buildInvalidSelection();
        }
        final String languageCode = resolveLanguageOption(dtmfDigits);
        if (StringUtils.isBlank(languageCode)) {
            return VoiceXmlBuilder.buildInvalidSelection();
        }
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
        return presentMenu(session, properties.getVoiceIvr().getMainMenuKey(), languageCode, true);
    }

    private String processSelection(final VoiceIvrSession session, final String dtmfDigits) {
        if (!NumberUtils.isDigits(dtmfDigits)) {
            return VoiceXmlBuilder.buildInvalidSelection();
        }
        final String language = resolveLanguage(session);
        final VoiceIvrMenuOption option = menuOptionRepository
                .findByMenuKeyAndLanguageCodeAndOptionDigitAndEnabledTrue(session.getCurrentMenuKey(), language,
                        Integer.parseInt(dtmfDigits))
                .orElse(null);
        if (option == null) {
            return VoiceXmlBuilder.buildInvalidSelection();
        }
        return dispatchAction(session, option, language);
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
            case HANGUP -> {
                sessionService.markClosed(session);
                yield VoiceXmlBuilder.buildInvalidSelection();
            }
        };
    }

    private String navigateToSubmenu(final VoiceIvrSession session, final VoiceIvrMenuOption option) {
        session.setParentMenuKey(session.getCurrentMenuKey());
        session.setCurrentMenuKey(option.getActionTarget());
        sessionService.save(session);
        return presentMenu(session, option.getActionTarget(), resolveLanguage(session), true);
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

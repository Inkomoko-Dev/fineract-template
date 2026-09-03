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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSessionContext;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppMenuNavigationService {

    private final WhatsAppConversationSessionService sessionService;
    private final WhatsAppMenuRenderer menuRenderer;
    private final WhatsAppMenuDefinitionService menuDefinitionService;
    private final WhatsAppReplyService replyService;
    private final WhatsAppSessionContextSerializer contextSerializer;
    private final WhatsAppInteractiveProperties properties;

    @Transactional
    public void navigateToMenu(final WhatsAppConversationSession session, final String menuKey, final RecipientType recipientType,
            final Client client, final Staff staff, final boolean recordParent) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getDefaultLanguage());
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        if (recordParent) {
            context.setParentMenuKey(session.getCurrentMenuKey());
            session.setSessionContext(contextSerializer.toJson(context));
        }
        session.setCurrentMenuKey(menuKey);
        session.setSessionStatus(WhatsAppSessionStatus.MAIN_MENU);
        sessionService.save(session);
        sendMenu(session, recipientType, client, staff, language);
    }

    @Transactional
    public void navigateBack(final WhatsAppConversationSession session, final RecipientType recipientType, final Client client,
            final Staff staff) {
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getDefaultLanguage());
        final WhatsAppSessionContext context = contextSerializer.fromJson(session.getSessionContext());
        String targetMenu = context.getParentMenuKey();
        if (StringUtils.isBlank(targetMenu)) {
            targetMenu = menuDefinitionService.resolveParentMenuKey(session.getCurrentMenuKey(), language);
        }
        if (StringUtils.isBlank(targetMenu)) {
            targetMenu = properties.getMainMenuKey();
        }
        context.setParentMenuKey(null);
        session.setSessionContext(contextSerializer.toJson(context));
        session.setCurrentMenuKey(targetMenu);
        session.setSessionStatus(WhatsAppSessionStatus.MAIN_MENU);
        sessionService.save(session);
        sendMenu(session, recipientType, client, staff, language);
    }

    @Transactional
    public void navigateToMainMenu(final WhatsAppConversationSession session, final RecipientType recipientType, final Client client,
            final Staff staff) {
        session.setSessionContext(null);
        session.setCurrentMenuKey(properties.getMainMenuKey());
        session.setSessionStatus(WhatsAppSessionStatus.MAIN_MENU);
        sessionService.save(session);
        final String language = StringUtils.defaultIfBlank(session.getLanguageCode(), properties.getDefaultLanguage());
        sendMenu(session, recipientType, client, staff, language);
    }

    private void sendMenu(final WhatsAppConversationSession session, final RecipientType recipientType, final Client client,
            final Staff staff, final String language) {
        final String menuKey = session.getCurrentMenuKey();
        final String header = menuRenderer.resolveHeader(menuKey, language);
        final String menu = menuRenderer.renderMenu(menuKey, language, header);
        replyService.sendTransactionalReply(session.getPhoneNumber(), recipientType, client, staff, menu);
    }
}

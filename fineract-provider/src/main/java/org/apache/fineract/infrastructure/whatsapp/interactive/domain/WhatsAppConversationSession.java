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
package org.apache.fineract.infrastructure.whatsapp.interactive.domain;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppConversationType;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppSessionStatus;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;

@Entity
@Table(name = "whatsapp_conversation_session")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppConversationSession extends AbstractPersistableCustom {

    @Column(name = "phone_number", length = 50, nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_type", length = 30, nullable = false)
    private WhatsAppConversationType conversationType;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "language_code", length = 15)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", length = 40, nullable = false)
    private WhatsAppSessionStatus sessionStatus;

    @Column(name = "current_menu_key", length = 100)
    private String currentMenuKey;

    @Column(name = "session_context")
    private String sessionContext;

    @Column(name = "authenticated", nullable = false)
    private boolean authenticated = false;

    @Column(name = "auth_expires_at")
    private LocalDateTime authExpiresAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "lastmodified_date", nullable = false)
    private LocalDateTime lastModifiedDate;

    public static WhatsAppConversationSession startNew(final String phoneNumber, final WhatsAppConversationType conversationType,
            final Client client, final Staff staff, final LocalDateTime expiresAt) {
        final WhatsAppConversationSession session = new WhatsAppConversationSession();
        session.phoneNumber = phoneNumber;
        session.conversationType = conversationType;
        session.client = client;
        session.staff = staff;
        session.sessionStatus = WhatsAppSessionStatus.LANGUAGE_SELECTION;
        session.currentMenuKey = "LANGUAGE";
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        session.createdDate = now;
        session.lastModifiedDate = now;
        session.lastActivityAt = now;
        session.expiresAt = expiresAt;
        return session;
    }

    public void touchActivity(final LocalDateTime expiresAt) {
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        this.lastActivityAt = now;
        this.lastModifiedDate = now;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(final LocalDateTime now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}

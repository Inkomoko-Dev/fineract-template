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
package org.apache.fineract.infrastructure.campaigns.whatsapp.domain;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppAtTemplateApprovalStatus;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * Catalog entry for an Africa's Talking WhatsApp template created via CBS.
 */
@Entity
@Table(name = "whatsapp_at_template", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "name", "language_code" }, name = "uk_whatsapp_at_template_name_lang") })
public class WhatsAppAtTemplate extends AbstractPersistableCustom {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "language_code", nullable = false, length = 20)
    private String languageCode;

    @Column(name = "category", length = 40)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private WhatsAppAtTemplateApprovalStatus approvalStatus;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    protected WhatsAppAtTemplate() {}

    public WhatsAppAtTemplate(final String name, final String languageCode, final String category,
            final WhatsAppAtTemplateApprovalStatus approvalStatus, final LocalDateTime createdDate) {
        this.name = name;
        this.languageCode = languageCode;
        this.category = category;
        this.approvalStatus = approvalStatus;
        this.createdDate = createdDate;
    }

    public String getName() {
        return name;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String getCategory() {
        return category;
    }

    public WhatsAppAtTemplateApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }

    public void updateCategory(final String category) {
        this.category = category;
    }

    public void updateApprovalStatus(final WhatsAppAtTemplateApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }
}

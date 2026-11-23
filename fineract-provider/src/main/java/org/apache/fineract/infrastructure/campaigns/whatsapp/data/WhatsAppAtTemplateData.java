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
package org.apache.fineract.infrastructure.campaigns.whatsapp.data;

/**
 * Read DTO for a catalogued Africa's Talking WhatsApp template.
 */
@SuppressWarnings("unused")
public final class WhatsAppAtTemplateData {

    private final Long id;
    private final String name;
    private final String languageCode;
    private final String category;
    private final String approvalStatus;

    private WhatsAppAtTemplateData(final Long id, final String name, final String languageCode, final String category,
            final String approvalStatus) {
        this.id = id;
        this.name = name;
        this.languageCode = languageCode;
        this.category = category;
        this.approvalStatus = approvalStatus;
    }

    public static WhatsAppAtTemplateData instance(final Long id, final String name, final String languageCode, final String category,
            final String approvalStatus) {
        return new WhatsAppAtTemplateData(id, name, languageCode, category, approvalStatus);
    }

    public Long getId() {
        return id;
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

    public String getApprovalStatus() {
        return approvalStatus;
    }
}

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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.campaigns.whatsapp.constants.WhatsAppAtTemplateApprovalStatus;
import org.apache.fineract.infrastructure.campaigns.whatsapp.data.WhatsAppAtTemplateData;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppAtTemplate;
import org.apache.fineract.infrastructure.campaigns.whatsapp.domain.WhatsAppAtTemplateRepository;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists and lists locally catalogued Africa's Talking WhatsApp templates.
 */
@Service
public class WhatsAppAtTemplateCatalogService {

    public static final String MARK_AS_APPROVED_PARAM = "markAsApproved";

    private final WhatsAppAtTemplateRepository repository;
    private final FromJsonHelper fromJsonHelper;

    public WhatsAppAtTemplateCatalogService(final WhatsAppAtTemplateRepository repository, final FromJsonHelper fromJsonHelper) {
        this.repository = repository;
        this.fromJsonHelper = fromJsonHelper;
    }

    /**
     * Removes catalog-only fields so the payload can be forwarded to Africa's Talking.
     */
    public String stripCatalogOnlyFields(final String apiRequestBodyAsJson) {
        final JsonElement element = this.fromJsonHelper.parse(apiRequestBodyAsJson);
        if (element == null || !element.isJsonObject()) {
            return apiRequestBodyAsJson;
        }
        final JsonObject body = element.getAsJsonObject();
        body.remove(MARK_AS_APPROVED_PARAM);
        return body.toString();
    }

    @Transactional
    public WhatsAppAtTemplate upsertFromCreateRequest(final String apiRequestBodyAsJson) {
        final JsonElement element = this.fromJsonHelper.parse(apiRequestBodyAsJson);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("WhatsApp template request body must be a JSON object.");
        }
        final JsonObject body = element.getAsJsonObject();
        final String name = body.has("name") && !body.get("name").isJsonNull() ? StringUtils.trimToEmpty(body.get("name").getAsString())
                : "";
        final String language = body.has("language") && !body.get("language").isJsonNull()
                ? StringUtils.trimToEmpty(body.get("language").getAsString())
                : "";
        final String category = body.has("category") && !body.get("category").isJsonNull()
                ? StringUtils.trimToNull(body.get("category").getAsString())
                : null;
        final boolean markAsApproved = body.has(MARK_AS_APPROVED_PARAM) && !body.get(MARK_AS_APPROVED_PARAM).isJsonNull()
                && body.get(MARK_AS_APPROVED_PARAM).getAsBoolean();
        final WhatsAppAtTemplateApprovalStatus approvalStatus = WhatsAppAtTemplateApprovalStatus.fromMarkAsApproved(markAsApproved);
        if (StringUtils.isBlank(name) || StringUtils.isBlank(language)) {
            throw new IllegalArgumentException("WhatsApp template name and language are required to catalog the template.");
        }
        return this.repository.findByNameAndLanguageCode(name, language).map(existing -> {
            existing.updateCategory(category);
            // Never downgrade APPROVED → PENDING on recreate; allow explicit approve.
            if (approvalStatus == WhatsAppAtTemplateApprovalStatus.APPROVED
                    || existing.getApprovalStatus() != WhatsAppAtTemplateApprovalStatus.APPROVED) {
                existing.updateApprovalStatus(approvalStatus);
            }
            return this.repository.save(existing);
        }).orElseGet(() -> this.repository
                .save(new WhatsAppAtTemplate(name, language, category, approvalStatus, DateUtils.getLocalDateTimeOfTenant())));
    }

    /**
     * Campaign TEMPLATE pickers only see approved templates.
     */
    @Transactional(readOnly = true)
    public Collection<WhatsAppAtTemplateData> retrieveApproved() {
        final List<WhatsAppAtTemplate> rows = this.repository
                .findByApprovalStatusOrderByNameAscLanguageCodeAsc(WhatsAppAtTemplateApprovalStatus.APPROVED);
        return rows.stream()
                .map(row -> WhatsAppAtTemplateData.instance(row.getId(), row.getName(), row.getLanguageCode(), row.getCategory(),
                        row.getApprovalStatus().name()))
                .collect(Collectors.toList());
    }
}

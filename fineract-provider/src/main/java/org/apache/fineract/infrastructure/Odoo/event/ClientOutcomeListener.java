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
package org.apache.fineract.infrastructure.Odoo.event;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.fineract.infrastructure.Odoo.OdooService;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateReadPlatformService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.TenantDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "fineract.integrations.kafka.enabled", havingValue = "true")
public class ClientOutcomeListener {

    private static final Logger LOG = LoggerFactory.getLogger(ClientOutcomeListener.class);

    private static final String OUTCOME_EVENT_TYPE = "CBS_CLIENT_OUTCOME";

    private final OdooService odooService;
    private final TenantDetailsService tenantDetailsService;
    private final BusinessDateReadPlatformService businessDateReadPlatformService;

    @Value("${fineract.integrations.events.tenant-identifier}")
    private String tenantIdentifier;

    public ClientOutcomeListener(OdooService odooService, TenantDetailsService tenantDetailsService,
            BusinessDateReadPlatformService businessDateReadPlatformService) {
        this.odooService = odooService;
        this.tenantDetailsService = tenantDetailsService;
        this.businessDateReadPlatformService = businessDateReadPlatformService;
    }

    @KafkaListener(topics = "${fineract.integrations.events.client-outcome-topic}", groupId = "${fineract.integrations.events.client-outcome-consumer-group}", autoStartup = "${fineract.integrations.events.outcome-consumer-enabled}", containerFactory = "journalEntryOutcomeListenerContainerFactory")
    public void onOutcome(String message) {
        JsonObject envelope = JsonParser.parseString(message).getAsJsonObject();
        String eventId = envelope.has("eventId") ? envelope.get("eventId").getAsString() : null;
        String eventType = envelope.has("eventType") ? envelope.get("eventType").getAsString() : null;

        if (!OUTCOME_EVENT_TYPE.equals(eventType)) {
            LOG.warn("Skipping event {} with unexpected eventType {} on client outcome topic", eventId, eventType);
            return;
        }
        if (!envelope.has("payload") || !envelope.get("payload").isJsonObject()) {
            LOG.warn("Skipping client outcome event {} without a payload object", eventId);
            return;
        }

        try {
            // listener threads carry no tenant/business-date context
            FineractPlatformTenant tenant = tenantDetailsService.loadTenantById(tenantIdentifier);
            ThreadLocalContextUtil.setTenant(tenant);
            ThreadLocalContextUtil.setBusinessDates(businessDateReadPlatformService.getBusinessDates());
            odooService.applyClientSyncOutcome(envelope.getAsJsonObject("payload"));
        } finally {
            ThreadLocalContextUtil.clearTenant();
        }
    }
}

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
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

// async replacement for the response body the SYNC path applied inline; a lost outcome self-heals via the posting cron's retry
@Service
public class JournalEntryOutcomeListener {

    private static final Logger LOG = LoggerFactory.getLogger(JournalEntryOutcomeListener.class);

    private static final String OUTCOME_EVENT_TYPE = "CBS_JOURNAL_ENTRY_OUTCOME";

    private final OdooService odooService;
    private final TenantDetailsService tenantDetailsService;
    private final BusinessDateReadPlatformService businessDateReadPlatformService;

    @Value("${fineract.integrations.events.tenant-identifier}")
    private String tenantIdentifier;

    public JournalEntryOutcomeListener(OdooService odooService, TenantDetailsService tenantDetailsService,
            BusinessDateReadPlatformService businessDateReadPlatformService) {
        this.odooService = odooService;
        this.tenantDetailsService = tenantDetailsService;
        this.businessDateReadPlatformService = businessDateReadPlatformService;
    }

    @KafkaListener(topics = "${fineract.integrations.events.journal-entry-outcome-topic}", groupId = "${fineract.integrations.events.outcome-consumer-group}", autoStartup = "${fineract.integrations.events.outcome-consumer-enabled}")
    public void onOutcome(String message) {
        JsonObject envelope = JsonParser.parseString(message).getAsJsonObject();
        String eventId = envelope.has("eventId") ? envelope.get("eventId").getAsString() : null;
        String eventType = envelope.has("eventType") ? envelope.get("eventType").getAsString() : null;

        LOG.info("Received journal entry outcome event {} (eventType={})", eventId, eventType);
        LOG.debug("Inbound envelope for event {}: {}", eventId, envelope);

        if (!OUTCOME_EVENT_TYPE.equals(eventType)) {
            LOG.warn("Skipping event with unexpected eventType {} on outcome topic", eventType);
            return;
        }
        if (!envelope.has("payload") || !envelope.get("payload").isJsonObject()) {
            LOG.warn("Skipping outcome event {} without a payload object", eventId);
            return;
        }

        try {
            // Kafka listener threads carry no tenant/business-date context — bootstrap it like scheduler job threads do
            FineractPlatformTenant tenant = tenantDetailsService.loadTenantById(tenantIdentifier);
            ThreadLocalContextUtil.setTenant(tenant);
            ThreadLocalContextUtil.setBusinessDates(businessDateReadPlatformService.getBusinessDates());

            String payload = envelope.getAsJsonObject("payload").toString();
            LOG.debug("Odoo response payload for event {}: {}", eventId, payload);

            String result = odooService.updateJournalEntryWithOdooStatus(payload);
            LOG.info("Applied journal entry outcome event {}: {}", eventId, result);
        } finally {
            ThreadLocalContextUtil.clearTenant();
        }
    }
}

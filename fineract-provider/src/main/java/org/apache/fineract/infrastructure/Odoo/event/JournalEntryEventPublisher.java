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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

// partition key is resourceId (loan transaction id) so a create and its reversal stay ordered on one partition
@Service
public class JournalEntryEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(JournalEntryEventPublisher.class);

    private static final String EVENT_TYPE = "CBS_JOURNAL_ENTRY";
    private static final String SOURCE = "CBS";
    // kept just above delivery.timeout.ms so the broker-side timeout fires first
    private static final long SEND_TIMEOUT_SECONDS = 20;

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${fineract.integrations.events.journal-entry-topic}")
    private String topic;

    public JournalEntryEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public String publish(String resourceId, String journalEntryPayloadJson) {
        String eventId = UUID.randomUUID().toString();

        JsonObject envelope = new JsonObject();
        envelope.addProperty("eventId", eventId);
        envelope.addProperty("eventType", EVENT_TYPE);
        envelope.addProperty("occurredAt", Instant.now().toString());
        envelope.addProperty("source", SOURCE);
        envelope.addProperty("schemaVersion", 1);
        envelope.add("payload", JsonParser.parseString(journalEntryPayloadJson));

        LOG.info("Publishing journal entry event {} (key={}) to {}", eventId, resourceId, topic);
        LOG.debug("Outbound envelope for event {}: {}", eventId, envelope);

        try {
            kafkaTemplate.send(topic, resourceId, envelope.toString()).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            LOG.info("Published journal entry event {} (key={}) to {}", eventId, resourceId, topic);
            return eventId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneralPlatformDomainRuleException("error.msg.journal.entry.event.publish.failed",
                    "Interrurnal entry event for transpted while publishing jouaction " + resourceId);
        } catch (Exception e) {
            // failure leaves is_oddo_posted false, so the posting cron retries this transaction
            throw new GeneralPlatformDomainRuleException("error.msg.journal.entry.event.publish.failed",
                    "Failed to publish journal entry event for transaction " + resourceId + ": " + e.getMessage());
        }
    }
}

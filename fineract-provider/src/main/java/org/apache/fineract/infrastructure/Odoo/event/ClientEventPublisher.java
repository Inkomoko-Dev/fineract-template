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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

// partition key is the client id so a client's create and later updates stay ordered
@Service
@ConditionalOnProperty(name = "fineract.integrations.kafka.enabled", havingValue = "true")
public class ClientEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(ClientEventPublisher.class);

    private static final String EVENT_TYPE = "CBS_CLIENT";
    private static final String SOURCE = "CBS";
    private static final long SEND_TIMEOUT_SECONDS = 20;

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Value("${fineract.integrations.events.client-topic}")
    private String topic;

    public ClientEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public String publish(Long clientId, JsonObject clientPayload) {
        String eventId = UUID.randomUUID().toString();

        JsonObject envelope = new JsonObject();
        envelope.addProperty("eventId", eventId);
        envelope.addProperty("eventType", EVENT_TYPE);
        envelope.addProperty("occurredAt", Instant.now().toString());
        envelope.addProperty("source", SOURCE);
        envelope.addProperty("schemaVersion", 1);
        envelope.add("payload", clientPayload);

        LOG.debug("Outbound client envelope for event {}: {}", eventId, envelope);

        try {
            kafkaTemplate.send(topic, clientId.toString(), envelope.toString()).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            LOG.info("Published client event {} (clientId={}) to {}", eventId, clientId, topic);
            return eventId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneralPlatformDomainRuleException("error.msg.client.event.publish.failed",
                    "Interrupted while publishing client event for client " + clientId);
        } catch (Exception e) {
            // the client stays unsynced, so the client sync cron retries it
            throw new GeneralPlatformDomainRuleException("error.msg.client.event.publish.failed",
                    "Failed to publish client event for client " + clientId + ": " + e.getMessage());
        }
    }
}

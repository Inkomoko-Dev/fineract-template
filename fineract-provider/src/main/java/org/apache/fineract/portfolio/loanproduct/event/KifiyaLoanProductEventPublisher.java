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
package org.apache.fineract.portfolio.loanproduct.event;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KifiyaLoanProductEventPublisher {

    private static final String EVENT_TYPE = "CBS_LOAN_PRODUCT_THIRD_PARTY_DISBURSEMENT";
    private static final String SOURCE = "CBS";
    private static final long SEND_TIMEOUT_SECONDS = 20;

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final Gson gson = new Gson();

    @Value("${fineract.integrations.events.kifiya-loan-product-topic}")
    private String topic;

    @Value("${fineract.integrations.events.kifiya-loan-product-enabled:false}")
    private boolean enabled;

    public KifiyaLoanProductEventPublisher(final KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(final String action, final ThirdPartyDisbursementProductData product) {
        if (!this.enabled) {
            log.debug("Skipping Kifiya loan product event for product {} because publishing is disabled", product.getId());
            return;
        }

        final String eventId = UUID.randomUUID().toString();
        final JsonObject envelope = new JsonObject();
        envelope.addProperty("eventId", eventId);
        envelope.addProperty("eventType", EVENT_TYPE);
        envelope.addProperty("action", action);
        envelope.addProperty("occurredAt", Instant.now().toString());
        envelope.addProperty("source", SOURCE);
        envelope.addProperty("schemaVersion", 1);
        envelope.add("payload", JsonParser.parseString(this.gson.toJson(product)));

        final String partitionKey = String.valueOf(product.getId());
        log.info("Publishing Kifiya loan product event {} (action={}, key={}) to {}", eventId, action, partitionKey, this.topic);
        try {
            this.kafkaTemplate.send(this.topic, partitionKey, envelope.toString()).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Published Kifiya loan product event {} (action={}, key={})", eventId, action, partitionKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while publishing Kifiya loan product event {} for product {}", eventId, product.getId(), e);
        } catch (Exception e) {
            log.error("Failed to publish Kifiya loan product event {} for product {}: {}", eventId, product.getId(), e.getMessage(), e);
        }
    }
}

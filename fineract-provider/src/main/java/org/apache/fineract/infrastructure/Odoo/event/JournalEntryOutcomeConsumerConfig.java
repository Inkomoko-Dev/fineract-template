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

import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

// scoped to JournalEntryOutcomeListener only, built from the same spring.kafka.consumer.* settings
// as the app's default factory with just these overrides — so any future @KafkaListener in this app
// keeps the default batch size instead of silently inheriting these
@Configuration
public class JournalEntryOutcomeConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> journalEntryOutcomeListenerContainerFactory(
            KafkaProperties kafkaProperties,
            @Value("${fineract.integrations.events.outcome-consumer-concurrency}") int concurrency,
            @Value("${fineract.integrations.events.outcome-consumer-max-poll-records}") int maxPollRecords,
            // int, not long: ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG is Type.INT in the Kafka
            // client despite the "ms" name — a boxed Long here fails ConfigDef's type check at
            // KafkaConsumer construction and crashes listener container startup
            @Value("${fineract.integrations.events.outcome-consumer-max-poll-interval-ms}") int maxPollIntervalMs) {

        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProperties();
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        consumerProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);
        ConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        return factory;
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatsAppCampaignFreeformPreviewTest {

    @Test
    void doesNotProduceABlankedPreviewForAnUnresolvedPlaceholder() {
        final String preview = WhatsAppCampaignWritePlatformServiceImpl.previewFreeformMessage("Hello ${clientName}, ${amount}.",
                Map.of("clientName", "Lucy", "amount", ""));

        assertThat(preview).isEqualTo("Freeform preview unavailable: unresolved values: amount");
    }

    @Test
    void resolvesThePreviewWhenAllPlaceholdersHaveValues() {
        final String preview = WhatsAppCampaignWritePlatformServiceImpl.previewFreeformMessage("Hello ${clientName}.",
                Map.of("clientName", "Lucy"));

        assertThat(preview).isEqualTo("Hello Lucy.");
    }
}

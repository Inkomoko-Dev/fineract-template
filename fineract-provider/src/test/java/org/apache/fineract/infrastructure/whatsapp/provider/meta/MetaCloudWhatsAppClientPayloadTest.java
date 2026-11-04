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
package org.apache.fineract.infrastructure.whatsapp.provider.meta;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MetaCloudWhatsAppClientPayloadTest {

    @Test
    void extractsWhatsAppMessageIdFromGraphResponse() {
        final String body = "{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.HBgN\"}]}";
        assertEquals("wamid.HBgN", MetaCloudWhatsAppClient.extractMessageId(body));
    }

    @Test
    void buildsMessagesUrlFromProperties() {
        final MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setEnabled(true);
        properties.setAccessToken("token");
        properties.setPhoneNumberId("123456");
        properties.setApiVersion("v21.0");
        properties.setGraphBaseUrl("https://graph.facebook.com");
        final MetaCloudWhatsAppClient client = new MetaCloudWhatsAppClient(properties, null, null,
                new org.apache.fineract.infrastructure.whatsapp.provider.WhatsAppLanguageMapper(
                        new org.apache.fineract.infrastructure.whatsapp.provider.config.WhatsAppProviderProperties()));
        assertTrue(client.buildMessagesUrl().endsWith("/v21.0/123456/messages"));
    }
}

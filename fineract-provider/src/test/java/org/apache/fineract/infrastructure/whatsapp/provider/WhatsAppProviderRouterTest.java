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
package org.apache.fineract.infrastructure.whatsapp.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.whatsapp.provider.config.WhatsAppProviderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppProviderRouterTest {

    @Mock
    private WhatsAppTransport meta;
    @Mock
    private WhatsAppTransport at;

    @Test
    void fallsBackWhenPrimaryFails() {
        final WhatsAppProviderProperties properties = new WhatsAppProviderProperties();
        properties.setPrimaryProvider("META");
        properties.setFallbackProvider("AFRICASTALKING");
        properties.setFallbackEnabled(true);
        when(meta.provider()).thenReturn(WhatsAppProvider.META);
        when(at.provider()).thenReturn(WhatsAppProvider.AFRICASTALKING);
        when(meta.isConfigured()).thenReturn(true);
        when(at.isConfigured()).thenReturn(true);
        when(meta.sendText("+254700000000", "hi")).thenReturn(WhatsAppTransportResult.failure(WhatsAppProvider.META, 500, "err", "err"));
        when(at.sendText("+254700000000", "hi")).thenReturn(WhatsAppTransportResult.success(WhatsAppProvider.AFRICASTALKING, 200, "{}", "AT1"));

        final WhatsAppProviderRouter router = new WhatsAppProviderRouter(properties, List.of(meta, at));
        final WhatsAppTransportResult result = router.sendText("+254700000000", "hi");
        assertEquals(WhatsAppProvider.AFRICASTALKING, result.provider());
        assertEquals(true, result.successful());
    }
}

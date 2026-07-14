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
package org.apache.fineract.infrastructure.campaigns.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.apache.fineract.infrastructure.campaigns.sms.constants.SmsCampaignConstants;
import org.apache.fineract.infrastructure.campaigns.sms.data.MessageGatewayConfigurationData;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesPropertiesReadPlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
class SmsConfigUtilsTest {

    @Mock
    private ExternalServicesPropertiesReadPlatformService propertiesReadPlatformService;

    @InjectMocks
    private SmsConfigUtils smsConfigUtils;

    @Test
    void getMessageGateWayRequestURIIncludesXApiKeyHeader() {
        when(propertiesReadPlatformService.getSMSGateway()).thenReturn(
                new MessageGatewayConfigurationData(null, null, "192.168.1.26", 8005, "/api/sms", null, null, false, "sms_test_key"));

        Map<String, Object> details = smsConfigUtils.getMessageGateWayRequestURI("send", "{\"providerId\":1}");

        HttpEntity<?> entity = (HttpEntity<?>) details.get("entity");
        assertNotNull(entity);
        HttpHeaders headers = entity.getHeaders();
        assertEquals("sms_test_key", headers.getFirst(SmsCampaignConstants.SMS_API_KEY_HEADER));
    }
}

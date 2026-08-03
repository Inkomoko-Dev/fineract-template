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
package org.apache.fineract.infrastructure.africastalking.service;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.data.ConnectivityChannelResultData;
import org.apache.fineract.infrastructure.africastalking.data.ConnectivityTestResultData;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AfricasTalkingConnectivityService {

    private final AfricasTalkingProperties properties;
    private final AfricasTalkingClient africasTalkingClient;

    public ConnectivityTestResultData testConnectivity(final String channel) {
        final ConnectivityTestResultData result = new ConnectivityTestResultData(properties.isEnabled(), properties.isConfigured());
        if (!properties.isEnabled()) {
            result.addChannel(new ConnectivityChannelResultData("all", false, null,
                    "Integration disabled. Set AFRICASTALKING_ENABLED=true to enable."));
            return result;
        }
        if (!properties.isConfigured()) {
            result.addChannel(new ConnectivityChannelResultData("all", false, null,
                    "Integration not configured. Set AFRICASTALKING_USERNAME and AFRICASTALKING_API_KEY."));
            return result;
        }
        final String normalizedChannel = StringUtils.defaultIfBlank(channel, "all").toLowerCase();
        if ("all".equals(normalizedChannel) || "whatsapp".equals(normalizedChannel)) {
            result.addChannel(testSharedAuth("whatsapp"));
        }
        if ("all".equals(normalizedChannel) || "voice".equals(normalizedChannel)) {
            result.addChannel(testSharedAuth("voice"));
        }
        return result;
    }

    private ConnectivityChannelResultData testSharedAuth(final String channel) {
        final long startedAt = System.currentTimeMillis();
        try {
            final AfricasTalkingClient.AfricasTalkingApiResponse response = africasTalkingClient.executeUserLookup();
            final long latency = System.currentTimeMillis() - startedAt;
            if (response.isSuccessful()) {
                return new ConnectivityChannelResultData(channel, true, latency, "Authenticated successfully with AfricasTalking API");
            }
            return new ConnectivityChannelResultData(channel, false, latency,
                    "AfricasTalking API returned HTTP " + response.statusCode());
        } catch (IllegalStateException e) {
            return new ConnectivityChannelResultData(channel, false, null, e.getMessage());
        } catch (IOException e) {
            return new ConnectivityChannelResultData(channel, false, System.currentTimeMillis() - startedAt,
                    "Failed to reach AfricasTalking API");
        }
    }
}

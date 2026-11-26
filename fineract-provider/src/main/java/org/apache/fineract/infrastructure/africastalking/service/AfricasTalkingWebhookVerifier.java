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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AfricasTalkingWebhookVerifier {

    private static final Set<String> SOURCE_VERIFIED_EVENTS = Set.of("WHATSAPP_INBOUND", "WHATSAPP_STATUS", "VOICE_CALLBACK", "VOICE_EVENT");

    private final AfricasTalkingProperties properties;

    private final List<IpAddressMatcher> allowedSources;

    public AfricasTalkingWebhookVerifier(final AfricasTalkingProperties properties) {
        this.properties = properties;
        final String configuredCidrs = properties.getWebhook().getAllowedCidrs();
        allowedSources = StringUtils.isBlank(configuredCidrs) ? List.of()
                : Arrays.stream(configuredCidrs.split(",", -1)).map(String::strip).map(AfricasTalkingWebhookVerifier::parseCidr).toList();
    }

    private static IpAddressMatcher parseCidr(final String cidr) {
        final String[] parts = cidr.split("/", -1);
        if (parts.length != 2 || !isNumericAddress(parts[0]) || !parts[1].matches("[0-9]{1,3}")) {
            throw new IllegalArgumentException("Invalid AFRICASTALKING_WEBHOOK_ALLOWED_CIDRS entry");
        }
        try {
            final InetAddress address = InetAddress.getByName(parts[0]);
            final int prefix = Integer.parseInt(parts[1]);
            if (prefix > address.getAddress().length * Byte.SIZE) {
                throw new IllegalArgumentException("Invalid AFRICASTALKING_WEBHOOK_ALLOWED_CIDRS prefix");
            }
            return new IpAddressMatcher(address.getHostAddress() + "/" + prefix);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid AFRICASTALKING_WEBHOOK_ALLOWED_CIDRS address", e);
        }
    }

    private static boolean isNumericAddress(final String address) {
        return address != null && (address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")
                || (address.contains(":") && address.matches("[0-9a-fA-F:.]+")));
    }

    public void verify(final String eventType, final String rawPayload, final String signatureHeader, final String userAgent,
            final String peerAddress) {
        if (SOURCE_VERIFIED_EVENTS.contains(eventType)) {
            if (!isNumericAddress(peerAddress) || allowedSources.stream().noneMatch(source -> source.matches(peerAddress))) {
                throw new AfricasTalkingWebhookAuthenticationException("Africa's Talking webhook peer address is not allowed");
            }
            return;
        }
        final String secret = properties.getWebhook().getSecret();
        if (StringUtils.isNotBlank(secret)) {
            if (!AfricasTalkingClient.verifyHmacSha256(rawPayload, signatureHeader, secret)) {
                throw new AfricasTalkingWebhookAuthenticationException("Invalid AfricasTalking webhook signature");
            }
            return;
        }
        log.warn("AFRICASTALKING_WEBHOOK_SECRET is not configured; falling back to User-Agent validation");
        if (userAgent == null || !userAgent.contains(AfricasTalkingConstants.USER_AGENT_PREFIX)) {
            throw new AfricasTalkingWebhookAuthenticationException("Webhook request failed AfricasTalking User-Agent validation");
        }
    }
}

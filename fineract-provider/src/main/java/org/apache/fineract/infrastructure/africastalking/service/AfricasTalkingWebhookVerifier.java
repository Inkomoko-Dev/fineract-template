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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.AfricasTalkingConstants;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AfricasTalkingWebhookVerifier {

    private final AfricasTalkingProperties properties;

    public void verify(final String rawPayload, final String signatureHeader, final String userAgent) {
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

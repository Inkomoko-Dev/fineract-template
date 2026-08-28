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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AfricasTalkingWebhookVerifierTest {

    private AfricasTalkingWebhookVerifier verifier;
    private AfricasTalkingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AfricasTalkingProperties();
        verifier = new AfricasTalkingWebhookVerifier(properties);
    }

    @Test
    void acceptsValidHmacSignature() {
        final String payload = "{\"id\":\"ATX_123\"}";
        final String secret = "test-secret";
        properties.getWebhook().setSecret(secret);
        final String signature = computeSignature(payload, secret);
        assertDoesNotThrow(() -> verifier.verify(payload, signature, null));
    }

    @Test
    void rejectsInvalidHmacSignature() {
        properties.getWebhook().setSecret("test-secret");
        assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                () -> verifier.verify("{\"id\":\"ATX_123\"}", "invalid", null));
    }

    @Test
    void fallsBackToUserAgentWhenSecretMissing() {
        assertDoesNotThrow(() -> verifier.verify("{}", null, "AfricasTalking/v1"));
    }

    @Test
    void rejectsMissingUserAgentWhenSecretMissing() {
        final AfricasTalkingWebhookAuthenticationException exception = assertThrows(
                AfricasTalkingWebhookAuthenticationException.class, () -> verifier.verify("{}", null, "curl/8.0"));
        assertTrue(exception.getMessage().contains("User-Agent"));
    }

    private static String computeSignature(final String payload, final String secret) {
        try {
            final javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

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
        assertDoesNotThrow(() -> verifier.verify("WHATSAPP_INBOUND", payload, signature, null, null));
    }

    @Test
    void rejectsInvalidHmacSignature() {
        properties.getWebhook().setSecret("test-secret");
        assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                () -> verifier.verify("WHATSAPP_INBOUND", "{\"id\":\"ATX_123\"}", "invalid", null, null));
    }

    @Test
    void fallsBackToUserAgentWhenSecretMissing() {
        assertDoesNotThrow(() -> verifier.verify("WHATSAPP_INBOUND", "{}", null, "AfricasTalking/v1", null));
    }

    @Test
    void rejectsMissingUserAgentWhenSecretMissing() {
        final AfricasTalkingWebhookAuthenticationException exception = assertThrows(
                AfricasTalkingWebhookAuthenticationException.class, () -> verifier.verify("WHATSAPP_INBOUND", "{}", null, "curl/8.0", null));
        assertTrue(exception.getMessage().contains("User-Agent"));
    }

    @Test
    void acceptsProxyCredentialOnlyForSupportedCallbacks() {
        properties.getWebhook().setTrustedProxyEnabled(true);
        properties.getWebhook().setTrustedProxyCredential("proxy-test-credential");
        properties.getWebhook().setSecret("signing-test-secret");

        for (final String eventType : java.util.List.of("WHATSAPP_INBOUND", "WHATSAPP_STATUS", "VOICE_CALLBACK", "VOICE_EVENT")) {
            assertDoesNotThrow(() -> verifier.verify(eventType, "{}", null, null, "proxy-test-credential"));
            assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                    () -> verifier.verify(eventType, "{}", computeSignature("{}", "signing-test-secret"), "AfricasTalking/v1", null));
            assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                    () -> verifier.verify(eventType, "{}", null, null, "wrong-credential"));
        }
        assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                () -> verifier.verify("PING", "{}", null, null, "proxy-test-credential"));
        assertDoesNotThrow(() -> verifier.verify("PING", "{}", computeSignature("{}", "signing-test-secret"), null, null));
    }

    @Test
    void rejectsBlankProxyConfiguration() {
        properties.getWebhook().setTrustedProxyEnabled(true);
        assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                () -> verifier.verify("VOICE_CALLBACK", "{}", null, "AfricasTalking/v1", null));
    }

    @Test
    void proxyCredentialDoesNotEnableProxyMode() {
        properties.getWebhook().setTrustedProxyCredential("proxy-test-credential");
        properties.getWebhook().setSecret("signing-test-secret");
        assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                () -> verifier.verify("VOICE_CALLBACK", "{}", null, null, "proxy-test-credential"));
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

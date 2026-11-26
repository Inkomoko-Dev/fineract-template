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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.core.config.TomcatConnectorCustomizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

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
        assertDoesNotThrow(() -> verifier.verify("PING", payload, signature, null, null));
    }

    @Test
    void rejectsInvalidHmacSignature() {
        properties.getWebhook().setSecret("test-secret");
        assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                () -> verifier.verify("PING", "{\"id\":\"ATX_123\"}", "invalid", null, null));
    }

    @Test
    void fallsBackToUserAgentWhenSecretMissing() {
        assertDoesNotThrow(() -> verifier.verify("PING", "{}", null, "AfricasTalking/v1", null));
    }

    @Test
    void rejectsMissingUserAgentWhenSecretMissing() {
        final AfricasTalkingWebhookAuthenticationException exception = assertThrows(
                AfricasTalkingWebhookAuthenticationException.class, () -> verifier.verify("PING", "{}", null, "curl/8.0", null));
        assertTrue(exception.getMessage().contains("User-Agent"));
    }

    @Test
    void catchAllAcceptsBothAddressFamiliesForAllFourCallbacks() {
        properties.getWebhook().setAllowedCidrs("0.0.0.0/0,::/0");
        properties.getWebhook().setSecret("legacy-ping-secret");
        final var sourceVerifier = new AfricasTalkingWebhookVerifier(properties);

        for (final String eventType : List.of("WHATSAPP_INBOUND", "WHATSAPP_STATUS", "VOICE_CALLBACK", "VOICE_EVENT")) {
            assertDoesNotThrow(() -> sourceVerifier.verify(eventType, "{}", null, null, "192.0.2.20"));
            assertDoesNotThrow(() -> sourceVerifier.verify(eventType, "{}", null, null, "2001:db8::20"));
            assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                    () -> sourceVerifier.verify(eventType, "{}", null, "AfricasTalking/v1", null));
        }
        assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                () -> sourceVerifier.verify("PING", "{}", null, null, "192.0.2.20"));
    }

    @Test
    void restrictedAllowlistRejectsOffListPeers() {
        properties.getWebhook().setAllowedCidrs("192.0.2.0/24, 2001:db8::/32");
        final var sourceVerifier = new AfricasTalkingWebhookVerifier(properties);
        assertDoesNotThrow(() -> sourceVerifier.verify("VOICE_CALLBACK", "{}", null, null, "192.0.2.20"));
        assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                () -> sourceVerifier.verify("VOICE_CALLBACK", "{}", null, "AfricasTalking/v1", "198.51.100.20"));
    }

    @Test
    void emptyAllowlistRejectsCallbacksEvenWithLegacyCredentials() {
        properties.getWebhook().setSecret("legacy-ping-secret");
        final String signature = computeSignature("{}", properties.getWebhook().getSecret());
        assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                () -> verifier.verify("VOICE_CALLBACK", "{}", signature, "AfricasTalking/v1", "192.0.2.20"));
    }

    @Test
    void rejectsMalformedAllowlistAtStartup() {
        for (final String cidr : List.of("localhost/32", "127.1/32", "192.0.2.0", "192.0.2.0/33", "2001:db8::/129", "192.0.2.0/24,")) {
            properties.getWebhook().setAllowedCidrs(cidr);
            assertThrows(IllegalArgumentException.class, () -> new AfricasTalkingWebhookVerifier(properties));
        }
    }

    @Test
    void forwardedHeaderCannotOverrideCapturedPeerAddress() throws Exception {
        properties.getWebhook().setAllowedCidrs("192.0.2.0/24");
        final var sourceVerifier = new AfricasTalkingWebhookVerifier(properties);
        final var factory = new TomcatServletWebServerFactory();
        new TomcatConnectorCustomizer().customize(factory);
        final Valve captureValve = factory.getEngineValves().iterator().next();

        final var connection = mock(Request.class);
        final var servletRequest = new MockHttpServletRequest("POST", "/api/v1/africastalking/webhooks/voice/callback");
        servletRequest.addHeader("X-Forwarded-For", "192.0.2.20");
        when(connection.getPeerAddr()).thenReturn("198.51.100.20");
        final String attribute = TomcatConnectorCustomizer.TCP_PEER_ADDRESS_ATTRIBUTE;
        doAnswer(invocation -> {
            servletRequest.setAttribute(attribute, invocation.getArgument(1));
            return null;
        }).when(connection).setAttribute(attribute, "198.51.100.20");

        final Valve app = mock(Valve.class);
        captureValve.setNext(app);
        final var response = mock(Response.class);
        doAnswer(invocation -> {
            new ForwardedHeaderFilter().doFilter(servletRequest, new MockHttpServletResponse(), (request, reply) -> {
                assertEquals("192.0.2.20", request.getRemoteAddr());
                assertThrows(AfricasTalkingWebhookAuthenticationException.class,
                        () -> sourceVerifier.verify("VOICE_CALLBACK", "{}", null, null, (String) request.getAttribute(attribute)));
            });
            return null;
        }).when(app).invoke(connection, response);

        captureValve.invoke(connection, response);
        verify(app).invoke(connection, response);
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

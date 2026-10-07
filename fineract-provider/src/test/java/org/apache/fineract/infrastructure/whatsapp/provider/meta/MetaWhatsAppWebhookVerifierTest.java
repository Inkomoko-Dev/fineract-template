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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class MetaWhatsAppWebhookVerifierTest {

    @Test
    void acceptsSubscribeChallengeWithMatchingVerifyToken() {
        final MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setWebhookVerifyToken("verify-me");
        final MetaWhatsAppWebhookVerifier verifier = new MetaWhatsAppWebhookVerifier(properties);

        assertTrue(verifier.verifyChallenge("subscribe", "verify-me"));
        assertFalse(verifier.verifyChallenge("subscribe", "wrong"));
        assertFalse(verifier.verifyChallenge("unsubscribe", "verify-me"));
    }

    @Test
    void verifiesXHubSignature256() throws Exception {
        final MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setAppSecret("app-secret");
        final MetaWhatsAppWebhookVerifier verifier = new MetaWhatsAppWebhookVerifier(properties);
        final String body = "{\"object\":\"whatsapp_business_account\"}";
        final Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        final byte[] digest = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        final StringBuilder hex = new StringBuilder();
        for (final byte b : digest) {
            hex.append(String.format("%02x", b));
        }

        assertTrue(verifier.verifySignature(body, "sha256=" + hex));
        assertFalse(verifier.verifySignature(body, "sha256=deadbeef"));
        assertFalse(verifier.verifySignature(body, "bad"));
    }

    @Test
    void rejectsWhenAppSecretBlank() {
        final MetaWhatsAppProperties properties = new MetaWhatsAppProperties();
        properties.setAppSecret("");
        final MetaWhatsAppWebhookVerifier verifier = new MetaWhatsAppWebhookVerifier(properties);
        assertFalse(verifier.verifySignature("{}", "sha256=anything"));
    }
}

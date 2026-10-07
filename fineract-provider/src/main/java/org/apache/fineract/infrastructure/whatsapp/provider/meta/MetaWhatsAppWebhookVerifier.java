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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetaWhatsAppWebhookVerifier {

    private final MetaWhatsAppProperties properties;

    public boolean verifyChallenge(final String mode, final String verifyToken) {
        return "subscribe".equals(mode) && StringUtils.isNotBlank(properties.getWebhookVerifyToken())
                && properties.getWebhookVerifyToken().equals(verifyToken);
    }

    public boolean verifySignature(final String rawBody, final String signatureHeader) {
        if (StringUtils.isBlank(properties.getAppSecret())) {
            // Fail closed: permitAll webhook must not accept unsigned payloads when secret unset.
            log.warn("Meta WhatsApp app secret is blank; rejecting webhook signature verification");
            return false;
        }
        if (StringUtils.isBlank(signatureHeader) || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        final String provided = signatureHeader.substring("sha256=".length()).trim().toLowerCase();
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getAppSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            final byte[] digest = mac.doFinal(rawBody == null ? new byte[0] : rawBody.getBytes(StandardCharsets.UTF_8));
            final String computed = toHex(digest);
            return MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to verify Meta WhatsApp webhook signature", e);
            return false;
        }
    }

    private static String toHex(final byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

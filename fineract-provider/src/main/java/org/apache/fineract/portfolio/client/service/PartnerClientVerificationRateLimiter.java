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
package org.apache.fineract.portfolio.client.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.stereotype.Component;

@Component
public class PartnerClientVerificationRateLimiter {

    static final int MAX_ATTEMPTS_PER_WINDOW = 30;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String HASH_SALT = "partner-client-verify-v1";

    private final Map<String, List<Long>> attemptsByKey = new ConcurrentHashMap<>();

    public void check(final String nationalId, final String phoneNumber) {
        final String key = hashIdentifier(nationalId != null ? nationalId : phoneNumber);
        final long now = System.currentTimeMillis();
        final long cutoff = now - WINDOW.toMillis();
        final List<Long> attempts = this.attemptsByKey.computeIfAbsent(key, ignored -> new ArrayList<>());
        synchronized (attempts) {
            for (final Iterator<Long> iterator = attempts.iterator(); iterator.hasNext();) {
                if (iterator.next() < cutoff) {
                    iterator.remove();
                }
            }
            if (attempts.size() >= MAX_ATTEMPTS_PER_WINDOW) {
                throw new PlatformApiDataValidationException("validation.msg.partnerClient.verify.rateLimited",
                        "Too many verification attempts. Try again later.",
                        List.of(ApiParameterError.generalError("validation.msg.partnerClient.verify.rateLimited",
                                "Too many verification attempts. Try again later.")));
            }
            attempts.add(now);
        }
    }

    static String hashIdentifier(final String value) {
        if (value == null || value.isBlank()) {
            return "empty";
        }
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(HASH_SALT.getBytes(StandardCharsets.UTF_8));
            digest.update(value.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException e) {
            return Integer.toHexString(value.hashCode());
        }
    }
}

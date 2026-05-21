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
package org.apache.fineract.infrastructure.core.service;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationHttpRetryService {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 2_000;
    private static final long DEFAULT_MAX_BACKOFF_MILLIS = 10_000;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;

    private final Environment env;

    public Response execute(String integrationName, String operationName, OkHttpClient client, Request request) throws IOException {
        int maxAttempts = Math.max(1, getInt("fineract.integrations.http.retry.maxAttempts", DEFAULT_MAX_ATTEMPTS));
        long initialBackoffMillis = Math.max(0, getLong("fineract.integrations.http.retry.initialBackoffMillis",
                DEFAULT_INITIAL_BACKOFF_MILLIS));
        long maxBackoffMillis = Math.max(initialBackoffMillis,
                getLong("fineract.integrations.http.retry.maxBackoffMillis", DEFAULT_MAX_BACKOFF_MILLIS));
        double multiplier = Math.max(1.0,
                getDouble("fineract.integrations.http.retry.backoffMultiplier", DEFAULT_BACKOFF_MULTIPLIER));

        IOException lastIOException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Response response = client.newCall(request).execute();
                if (isRetryableStatus(response.code()) && attempt < maxAttempts) {
                    int statusCode = response.code();
                    response.close();
                    long backoffMillis = backoffMillis(attempt, initialBackoffMillis, maxBackoffMillis, multiplier);
                    log.warn("{} {} returned retryable HTTP {} on attempt {}/{}. Retrying in {} ms.", integrationName,
                            operationName, statusCode, attempt, maxAttempts, backoffMillis);
                    sleep(backoffMillis);
                    continue;
                }
                return response;
            } catch (IOException e) {
                lastIOException = e;
                if (attempt >= maxAttempts) {
                    throw e;
                }
                long backoffMillis = backoffMillis(attempt, initialBackoffMillis, maxBackoffMillis, multiplier);
                log.warn("{} {} failed on attempt {}/{}. Retrying in {} ms. Cause: {}", integrationName, operationName, attempt,
                        maxAttempts, backoffMillis, e.getMessage());
                sleep(backoffMillis);
            }
        }

        throw lastIOException == null ? new IOException("HTTP request failed without a response") : lastIOException;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    private long backoffMillis(int attempt, long initialBackoffMillis, long maxBackoffMillis, double multiplier) {
        double calculated = initialBackoffMillis * Math.pow(multiplier, attempt - 1);
        return Math.min((long) calculated, maxBackoffMillis);
    }

    private void sleep(long backoffMillis) throws IOException {
        if (backoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry integration request", e);
        }
    }

    private int getInt(String propertyName, int defaultValue) {
        String value = this.env.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private long getLong(String propertyName, long defaultValue) {
        String value = this.env.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    private double getDouble(String propertyName, double defaultValue) {
        String value = this.env.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Double.parseDouble(value);
    }
}

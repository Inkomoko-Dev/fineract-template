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
package org.apache.fineract.portfolio.creditdecision.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class HttpCreditDecisionEngineTransport implements CreditDecisionEngineTransport {

    /** One shared client. The per-request timeout below also covers a connection that never opens. */
    private final HttpClient client = HttpClient.newHttpClient();

    @Override
    public Response post(final String url, final String jsonBody, final int timeoutSeconds, final String bearerToken) throws IOException {
        final URI uri;
        try {
            uri = URI.create(url);
        } catch (final IllegalArgumentException e) {
            throw new IOException("The engine address is not a valid URL");
        }
        if (uri.getScheme() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IOException("The engine address must start with http:// or https://");
        }
        final Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        final HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(timeout).header("Content-Type", "application/json")
                .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (bearerToken != null && !bearerToken.isBlank()) {
            request.header("Authorization", "Bearer " + bearerToken.trim());
        }
        try {
            final HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.statusCode(), response.body());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("The request to the engine was interrupted");
        }
    }
}

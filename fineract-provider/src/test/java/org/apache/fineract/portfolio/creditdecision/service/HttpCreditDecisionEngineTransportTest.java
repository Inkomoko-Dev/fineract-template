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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpCreditDecisionEngineTransportTest {

    private HttpServer server;
    private final AtomicReference<String> seenBody = new AtomicReference<>();
    private final AtomicReference<String> seenAuth = new AtomicReference<>();
    private final AtomicReference<String> seenType = new AtomicReference<>();
    private volatile int replyStatus = 200;
    private volatile String replyBody = "{\"decision\":\"GO\"}";
    private volatile long replyDelayMillis;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/decide", exchange -> {
            seenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            seenAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            seenType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            try {
                Thread.sleep(replyDelayMillis);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            final byte[] bytes = replyBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(replyStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/decide";
    }

    @Test
    void postsTheJsonAndReturnsStatusAndBody() throws IOException {
        final CreditDecisionEngineTransport.Response response = new HttpCreditDecisionEngineTransport().post(url(), "{\"a\":1}", 5, null);
        assertEquals(200, response.getStatus());
        assertEquals("{\"decision\":\"GO\"}", response.getBody());
        assertEquals("{\"a\":1}", seenBody.get());
        assertEquals("application/json", seenType.get());
        assertEquals(null, seenAuth.get());
    }

    @Test
    void sendsTheKeyAsABearerToken() throws IOException {
        new HttpCreditDecisionEngineTransport().post(url(), "{}", 5, "  secret-key ");
        assertEquals("Bearer secret-key", seenAuth.get());
    }

    @Test
    void anErrorStatusIsReturnedNotThrown() throws IOException {
        replyStatus = 503;
        replyBody = "busy";
        final CreditDecisionEngineTransport.Response response = new HttpCreditDecisionEngineTransport().post(url(), "{}", 5, null);
        assertEquals(503, response.getStatus());
        assertEquals("busy", response.getBody());
    }

    @Test
    void aSlowEngineIsGivenUpOnAfterTheTimeout() {
        replyDelayMillis = 3000;
        assertThrows(IOException.class, () -> new HttpCreditDecisionEngineTransport().post(url(), "{}", 1, null));
    }

    @Test
    void anAddressThatIsNotHttpIsRefused() {
        final HttpCreditDecisionEngineTransport transport = new HttpCreditDecisionEngineTransport();
        assertThrows(IOException.class, () -> transport.post("file:///etc/passwd", "{}", 5, null));
        assertThrows(IOException.class, () -> transport.post("not a url", "{}", 5, null));
    }
}

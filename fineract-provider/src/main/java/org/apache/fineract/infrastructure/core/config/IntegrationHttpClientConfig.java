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
package org.apache.fineract.infrastructure.core.config;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@RequiredArgsConstructor
public class IntegrationHttpClientConfig {

    private static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 15;
    private static final long DEFAULT_READ_TIMEOUT_SECONDS = 120;
    private static final long DEFAULT_WRITE_TIMEOUT_SECONDS = 60;
    private static final long DEFAULT_CALL_TIMEOUT_SECONDS = 180;
    private static final int DEFAULT_MAX_IDLE_CONNECTIONS = 10;
    private static final long DEFAULT_KEEP_ALIVE_MINUTES = 5;

    private final Environment env;

    @Bean
    @Qualifier("transUnionCrbHttpClient")
    public OkHttpClient transUnionCrbHttpClient() {
        return buildClient("fineract.integrations.transUnion.crb.http");
    }

    @Bean
    @Qualifier("kivaHttpClient")
    public OkHttpClient kivaHttpClient() {
        return buildClient("fineract.integrations.kiva.http");
    }

    @Bean
    @Qualifier("africasTalkingHttpClient")
    public OkHttpClient africasTalkingHttpClient() {
        return buildClient("fineract.integrations.africastalking.http");
    }

    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(DEFAULT_CONNECT_TIMEOUT_SECONDS));
        factory.setReadTimeout((int) TimeUnit.SECONDS.toMillis(DEFAULT_READ_TIMEOUT_SECONDS));
        return new RestTemplate(factory);
    }

    private OkHttpClient buildClient(String prefix) {
        return new OkHttpClient.Builder()
                .connectTimeout(getLong(prefix + ".connectTimeoutSeconds", DEFAULT_CONNECT_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .readTimeout(getLong(prefix + ".readTimeoutSeconds", DEFAULT_READ_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .writeTimeout(getLong(prefix + ".writeTimeoutSeconds", DEFAULT_WRITE_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .callTimeout(getLong(prefix + ".callTimeoutSeconds", DEFAULT_CALL_TIMEOUT_SECONDS), TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(getInt(prefix + ".maxIdleConnections", DEFAULT_MAX_IDLE_CONNECTIONS),
                        getLong(prefix + ".keepAliveMinutes", DEFAULT_KEEP_ALIVE_MINUTES), TimeUnit.MINUTES))
                .build();
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
}

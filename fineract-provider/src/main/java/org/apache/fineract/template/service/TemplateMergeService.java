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
package org.apache.fineract.template.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.template.domain.Template;
import org.apache.fineract.template.domain.TemplateFunctions;
import org.apache.fineract.template.domain.TemplateSyntax;
import org.apache.fineract.template.exception.TemplateForbiddenException;
import org.apache.fineract.template.exception.TemplateMapperFetchException;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class TemplateMergeService {

    private static final String API_PATH = "/api/v1/";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    private final FineractProperties fineractProperties;
    private final ServerProperties serverProperties;

    public String compile(final Template template, final Map<String, Object> scopes) throws IOException {
        final String authToken = ThreadLocalContextUtil.getAuthToken();
        return compile(template, scopes, authToken == null ? null : "Basic " + authToken);
    }

    public String compile(final Template template, final Map<String, Object> scopes, final String authorization) throws IOException {
        final Map<String, Object> mergeScopes = new HashMap<>(scopes);
        mergeScopes.put("static", new TemplateFunctions());

        final Mustache mustache = TemplateSyntax.compile(template.getText(), template.getName());

        for (final Map.Entry<String, String> mapper : template.getMappersAsMap().entrySet()) {
            final String url = resolveMapperUrl(mapper.getValue(), mergeScopes);
            try {
                final Map<String, Object> data = getMapFromUrl(url, authorization);
                formatDates(data);
                mergeScopes.put(mapper.getKey(), data);
            } catch (final IOException e) {
                log.warn("Template mapper {} could not be loaded from {}", mapper.getKey(), url, e);
                throw new TemplateMapperFetchException(mapper.getKey(), e);
            }
        }

        expandMapArrays(mergeScopes);

        final StringWriter stringWriter = new StringWriter();
        try {
            mustache.execute(stringWriter, mergeScopes);
        } catch (final MustacheException e) {
            final ApiParameterError error = ApiParameterError.parameterError("validation.msg.template.text.invalid.syntax",
                    "Template could not be rendered: " + e.getMessage(), "text", e.getMessage());
            throw new PlatformApiDataValidationException(List.of(error), e);
        }
        return stringWriter.toString();
    }

    private String resolveMapperUrl(final String mapperValue, final Map<String, Object> scopes) {
        final StringWriter stringWriter = new StringWriter();
        TemplateSyntax.compile(mapperValue, "mapper").execute(stringWriter, scopes);
        final String url = stringWriter.toString().trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            assertWhitelisted(url);
            return url;
        }
        return localApiBase() + StringUtils.removeStart(url, "/");
    }

    private String localApiBase() {
        final boolean ssl = this.serverProperties.getSsl() != null && this.serverProperties.getSsl().isEnabled();
        final InetAddress address = this.serverProperties.getAddress();
        String host = "localhost";
        if (address != null && !address.isAnyLocalAddress()) {
            host = address instanceof Inet6Address ? "[" + address.getHostAddress() + "]" : address.getHostAddress();
        }
        final int port = this.serverProperties.getPort() == null ? 8080 : this.serverProperties.getPort();
        final String configuredPath = StringUtils.defaultString(this.serverProperties.getServlet().getContextPath());
        final String contextPath = StringUtils.removeEnd(configuredPath, "/");
        return (ssl ? "https" : "http") + "://" + host + ":" + port + contextPath + API_PATH;
    }

    private void assertWhitelisted(final String url) {
        final FineractProperties.FineractTemplateProperties properties = this.fineractProperties.getTemplate();
        if (properties == null || !properties.isRegexWhitelistEnabled()) {
            return;
        }
        final List<String> whitelist = properties.getRegexWhitelist();
        if (whitelist != null) {
            for (final String urlPattern : whitelist) {
                if (Pattern.compile(urlPattern).matcher(url).matches()) {
                    return;
                }
            }
        }
        throw new TemplateForbiddenException(url);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapFromUrl(final String url, final String authorization) throws IOException {
        final HttpURLConnection connection = openConnection(url, authorization);
        try {
            final int status = connection.getResponseCode();
            if (status >= HttpURLConnection.HTTP_BAD_REQUEST) {
                throw new IOException("HTTP " + status + " from " + url);
            }
            final String response = getStringFromInputStream(connection.getInputStream());
            HashMap<String, Object> result = new HashMap<>();
            if ("text/plain".equals(connection.getContentType())) {
                result.put("src", response);
            } else {
                result = new ObjectMapper().readValue(response, HashMap.class);
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(final String url, final String authorization) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            TrustModifier.relaxHostChecking(connection);
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new IOException("Could not prepare the connection to " + url, e);
        }
        if (authorization != null) {
            connection.setRequestProperty("Authorization", authorization);
        }
        final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        if (tenant != null) {
            connection.setRequestProperty("Fineract-Platform-TenantId", tenant.getTenantIdentifier());
        }
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoInput(true);
        return connection;
    }

    @SuppressWarnings("unchecked")
    private void formatDates(final Object value) {
        if (value instanceof Map) {
            for (final Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                final LocalDate date = entry.getKey().endsWith("Date") ? asDate(entry.getValue()) : null;
                if (date != null) {
                    entry.setValue(date.format(DATE_FORMAT));
                } else {
                    formatDates(entry.getValue());
                }
            }
        } else if (value instanceof Iterable) {
            for (final Object item : (Iterable<Object>) value) {
                formatDates(item);
            }
        }
    }

    private static LocalDate asDate(final Object value) {
        if (!(value instanceof List) || ((List<?>) value).size() != 3) {
            return null;
        }
        final List<?> parts = (List<?>) value;
        if (!(parts.get(0) instanceof Integer && parts.get(1) instanceof Integer && parts.get(2) instanceof Integer)) {
            return null;
        }
        try {
            return LocalDate.of((Integer) parts.get(0), (Integer) parts.get(1), (Integer) parts.get(2));
        } catch (DateTimeException e) {
            return null;
        }
    }

    // TODO Replace this with appropriate alternative available in Guava
    private static String getStringFromInputStream(final InputStream is) {
        BufferedReader br = null;
        final StringBuilder sb = new StringBuilder();

        String line;
        try {

            br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (final IOException e) {
            log.error("getStringFromInputStream() failed", e);
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (final IOException e) {
                    log.error("Problem occurred in getStringFromInputStream function", e);
                }
            }
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void expandMapArrays(Object value) {
        if (value instanceof Map) {
            Map<String, Object> valueAsMap = (Map<String, Object>) value;
            // Map<String, Object> newValue = null;
            Map<String, Object> valueAsMap_second = new HashMap<>();
            for (Map.Entry<String, Object> valueAsMapEntry : valueAsMap.entrySet()) {
                Object valueAsMapEntryValue = valueAsMapEntry.getValue();
                if (valueAsMapEntryValue instanceof Map) { // JSON Object
                    expandMapArrays(valueAsMapEntryValue);
                } else if (valueAsMapEntryValue instanceof Iterable) { // JSON
                                                                       // Array
                    Iterable<Object> valueAsMapEntryValueIterable = (Iterable<Object>) valueAsMapEntryValue;
                    String valueAsMapEntryKey = valueAsMapEntry.getKey();
                    int i = 0;
                    for (Object object : valueAsMapEntryValueIterable) {
                        valueAsMap_second.put(valueAsMapEntryKey + "#" + i, object);
                        ++i;
                        expandMapArrays(object);

                    }
                }

            }
            valueAsMap.putAll(valueAsMap_second);

        }
    }

}

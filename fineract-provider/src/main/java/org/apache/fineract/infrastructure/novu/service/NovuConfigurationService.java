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
package org.apache.fineract.infrastructure.novu.service;

import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.configuration.service.ExternalServicesConstants;
import org.apache.fineract.infrastructure.novu.data.NovuConfigurationData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NovuConfigurationService {

    private final JdbcTemplate jdbcTemplate;

    public NovuConfigurationService(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NovuConfigurationData getConfiguration() {
        final Map<String, String> values = new HashMap<>();
        jdbcTemplate.query("SELECT esp.name, esp.value FROM c_external_service_properties esp "
                + "JOIN c_external_service es ON es.id = esp.external_service_id WHERE es.name = ?", rs -> {
                    values.put(rs.getString("name"), rs.getString("value"));
                }, ExternalServicesConstants.NOVU_SERVICE_NAME);
        final int timeout = parseTimeout(values.get(ExternalServicesConstants.NOVU_TIMEOUT_SECONDS));
        return new NovuConfigurationData(values.getOrDefault(ExternalServicesConstants.NOVU_API_URL, "https://api.novu.co"),
                values.getOrDefault(ExternalServicesConstants.NOVU_API_KEY, ""),
                Boolean.parseBoolean(values.getOrDefault(ExternalServicesConstants.NOVU_ENABLED, "false")), timeout);
    }

    private int parseTimeout(final String value) {
        try {
            return Math.max(1, Math.min(60, Integer.parseInt(value)));
        } catch (final NumberFormatException ignored) {
            return 10;
        }
    }
}

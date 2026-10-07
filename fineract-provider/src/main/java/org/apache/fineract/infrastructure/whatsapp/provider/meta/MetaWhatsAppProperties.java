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

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fineract.integrations.meta.whatsapp")
public class MetaWhatsAppProperties {

    private boolean enabled;
    private String accessToken;
    private String phoneNumberId;
    private String wabaId;
    private String apiVersion = "v21.0";
    private String graphBaseUrl = "https://graph.facebook.com";
    private String appSecret;
    private String webhookVerifyToken;

    public boolean isConfigured() {
        return enabled && StringUtils.isNoneBlank(accessToken, phoneNumberId);
    }

    public boolean isWebhookConfigured() {
        return StringUtils.isNoneBlank(appSecret, webhookVerifyToken);
    }
}

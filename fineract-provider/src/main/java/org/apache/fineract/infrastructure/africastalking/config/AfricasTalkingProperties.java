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
package org.apache.fineract.infrastructure.africastalking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fineract.integrations.africastalking")
public class AfricasTalkingProperties {

    private boolean enabled;
    private String username;
    private String apiKey;
    private String baseUrl = "https://api.africastalking.com";
    private String whatsappBaseUrl = "https://chat.africastalking.com";
    private String voiceBaseUrl = "https://voice.africastalking.com";
    private final Whatsapp whatsapp = new Whatsapp();
    private final Voice voice = new Voice();
    private final Webhook webhook = new Webhook();
    private final Phone phone = new Phone();

    public boolean isConfigured() {
        return username != null && !username.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    @Getter
    @Setter
    public static class Whatsapp {

        private String senderNumber;
    }

    @Getter
    @Setter
    public static class Voice {

        private String callerId;
        private String loansDepartmentNumber;
        private String supportDepartmentNumber;
        private String internalDepartmentNumber;
        private String businessHoursStart = "08:00";
        private String businessHoursEnd = "17:00";
        private String businessTimeZone = "Africa/Nairobi";
    }

    @Getter
    @Setter
    public static class Webhook {

        private String secret;
        private String signatureHeader = "X-AfricasTalking-Signature";
    }

    @Getter
    @Setter
    public static class Phone {

        private String defaultCountryCode = "254";
    }
}

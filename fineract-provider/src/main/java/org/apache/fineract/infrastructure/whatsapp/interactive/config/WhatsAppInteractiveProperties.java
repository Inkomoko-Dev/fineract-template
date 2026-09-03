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
package org.apache.fineract.infrastructure.whatsapp.interactive.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "fineract.integrations.whatsapp.interactive")
public class WhatsAppInteractiveProperties {

    private int sessionTimeoutMinutes = 30;
    private String defaultLanguage = "en";
    private String consentTermsVersion = "v1";
    private String optOutKeywords = "STOP,UNSUBSCRIBE,END,ACHA";
    private String optInKeywords = "START,SUBSCRIBE,YES";
    private String consentAcceptKeywords = "YES,AGREE,OK";
}

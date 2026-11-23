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
package org.apache.fineract.infrastructure.notifications.policy;

import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;

/**
 * Maps WhatsApp outbound policy denial codes to API domain-rule exceptions.
 */
public final class WhatsAppOutboundPolicyExceptionFactory {

    private WhatsAppOutboundPolicyExceptionFactory() {}

    public static GeneralPlatformDomainRuleException create(final String phoneNumber, final String denialCode) {
        return switch (StringUtils.defaultString(denialCode)) {
            case "OPTED_OUT" -> new GeneralPlatformDomainRuleException("error.msg.whatsapp.outbound.opted.out",
                    "Cannot send WhatsApp: recipient `" + phoneNumber + "` has opted out.", phoneNumber);
            case "CONSENT_REQUIRED" -> new GeneralPlatformDomainRuleException("error.msg.whatsapp.outbound.consent.required",
                    "Cannot send WhatsApp: recipient `" + phoneNumber
                            + "` has not granted consent. Ask them to reply with an accept keyword first.",
                    phoneNumber);
            case "CONSENT_DENIED" -> new GeneralPlatformDomainRuleException("error.msg.whatsapp.outbound.consent.denied",
                    "Cannot send WhatsApp: recipient `" + phoneNumber + "` previously declined consent.", phoneNumber);
            default -> new GeneralPlatformDomainRuleException("error.msg.whatsapp.outbound.blocked.by.policy",
                    "Cannot send WhatsApp: outbound message blocked by consent/opt-out policy"
                            + (StringUtils.isBlank(denialCode) ? "." : " (" + denialCode + ")."),
                    phoneNumber);
        };
    }
}

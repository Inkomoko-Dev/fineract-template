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
package org.apache.fineract.infrastructure.campaigns.whatsapp.constants;

public enum WhatsAppCampaignStatus {

    INVALID(-1, "whatsappCampaignStatus.invalid"), //
    PENDING(100, "whatsappCampaignStatus.pending"), //
    ACTIVE(300, "whatsappCampaignStatus.active"), //
    CLOSED(600, "whatsappCampaignStatus.closed");

    private final Integer value;
    private final String code;

    WhatsAppCampaignStatus(Integer value, String code) {
        this.value = value;
        this.code = code;
    }

    public static WhatsAppCampaignStatus fromInt(final Integer statusValue) {

        WhatsAppCampaignStatus enumeration = WhatsAppCampaignStatus.INVALID;
        switch (statusValue) {
            case 100:
                enumeration = WhatsAppCampaignStatus.PENDING;
            break;
            case 300:
                enumeration = WhatsAppCampaignStatus.ACTIVE;
            break;
            case 600:
                enumeration = WhatsAppCampaignStatus.CLOSED;
            break;
        }
        return enumeration;
    }

    public Integer getValue() {
        return value;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return this.value.equals(WhatsAppCampaignStatus.ACTIVE.getValue());
    }

    public boolean isPending() {
        return this.value.equals(WhatsAppCampaignStatus.PENDING.getValue());
    }

    public boolean isClosed() {
        return this.value.equals(WhatsAppCampaignStatus.CLOSED.getValue());
    }
}

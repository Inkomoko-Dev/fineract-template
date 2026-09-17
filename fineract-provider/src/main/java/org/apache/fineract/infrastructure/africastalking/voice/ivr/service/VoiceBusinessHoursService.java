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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppBusinessHoursService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceBusinessHoursService {

    private final WhatsAppBusinessHoursService whatsAppBusinessHoursService;
    private final AfricasTalkingProperties properties;

    @Transactional(readOnly = true)
    public boolean isWithinBusinessHours() {
        try {
            return whatsAppBusinessHoursService.isWithinBusinessHours(DateUtils.getLocalDateTimeOfTenant());
        } catch (final Exception e) {
            log.warn("Falling back to configured voice business hours window", e);
            return isWithinConfiguredWindow();
        }
    }

    @Transactional(readOnly = true)
    public String describeBusinessHours() {
        try {
            return whatsAppBusinessHoursService.describeBusinessHours();
        } catch (final Exception e) {
            final AfricasTalkingProperties.Voice voice = properties.getVoice();
            return voice.getBusinessHoursStart() + "-" + voice.getBusinessHoursEnd() + " " + voice.getBusinessTimeZone();
        }
    }

    private boolean isWithinConfiguredWindow() {
        final AfricasTalkingProperties.Voice voice = properties.getVoice();
        try {
            final ZoneId zoneId = ZoneId.of(voice.getBusinessTimeZone());
            final LocalTime now = ZonedDateTime.now(zoneId).toLocalTime();
            final LocalTime start = LocalTime.parse(voice.getBusinessHoursStart());
            final LocalTime end = LocalTime.parse(voice.getBusinessHoursEnd());
            return !now.isBefore(start) && now.isBefore(end);
        } catch (final Exception e) {
            log.warn("Unable to evaluate configured voice business hours; defaulting to open", e);
            return true;
        }
    }
}

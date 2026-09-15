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
package org.apache.fineract.infrastructure.whatsapp.interactive.service;

import com.google.gson.JsonElement;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppBusinessHoursData;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppBusinessHours;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppBusinessHoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppBusinessHoursWritePlatformService {

    private final FromJsonHelper fromJsonHelper;
    private final WhatsAppBusinessHoursRepository businessHoursRepository;

    @Transactional(readOnly = true)
    public List<WhatsAppBusinessHoursData> retrieveBusinessHours() {
        return businessHoursRepository.findAllByOrderByDayOfWeekAsc().stream().map(this::map).collect(Collectors.toList());
    }

    @Transactional
    public CommandProcessingResult updateBusinessHours(final Long businessHoursId, final String json) {
        final WhatsAppBusinessHours hours = businessHoursRepository.findById(businessHoursId)
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp business hours not found: " + businessHoursId));
        final JsonElement element = fromJsonHelper.parse(json);
        if (fromJsonHelper.parameterExists("openTime", element)) {
            hours.setOpenTime(fromJsonHelper.extractStringNamed("openTime", element));
        }
        if (fromJsonHelper.parameterExists("closeTime", element)) {
            hours.setCloseTime(fromJsonHelper.extractStringNamed("closeTime", element));
        }
        if (fromJsonHelper.parameterExists("enabled", element)) {
            hours.setEnabled(fromJsonHelper.extractBooleanNamed("enabled", element));
        }
        final WhatsAppBusinessHours saved = businessHoursRepository.save(hours);
        return new CommandProcessingResultBuilder().withEntityId(saved.getId()).build();
    }

    private WhatsAppBusinessHoursData map(final WhatsAppBusinessHours hours) {
        final WhatsAppBusinessHoursData data = new WhatsAppBusinessHoursData();
        data.setId(hours.getId());
        data.setDayOfWeek(hours.getDayOfWeek());
        data.setOpenTime(hours.getOpenTime());
        data.setCloseTime(hours.getCloseTime());
        data.setEnabled(hours.isEnabled());
        return data;
    }
}

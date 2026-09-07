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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.service.WhatsAppInteractiveSettingsProvider;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppBusinessHours;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppBusinessHoursRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppBusinessHoursService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final WhatsAppBusinessHoursRepository businessHoursRepository;
    private final WhatsAppInteractiveSettingsProvider settings;

    @Transactional(readOnly = true)
    public boolean isWithinBusinessHours(final LocalDateTime dateTime) {
        final WhatsAppBusinessHours hours = businessHoursRepository.findByDayOfWeek(dateTime.getDayOfWeek().getValue()).orElse(null);
        if (hours == null || !hours.isEnabled()) {
            return false;
        }
        final LocalTime open = LocalTime.parse(hours.getOpenTime(), TIME_FORMAT);
        final LocalTime close = LocalTime.parse(hours.getCloseTime(), TIME_FORMAT);
        final LocalTime current = dateTime.toLocalTime();
        return !current.isBefore(open) && current.isBefore(close);
    }

    @Transactional(readOnly = true)
    public LocalDateTime calculateSlaDueAt(final LocalDateTime createdAt) {
        LocalDateTime dueAt = isWithinBusinessHours(createdAt) ? createdAt : nextBusinessOpen(createdAt);
        return dueAt.plusHours(settings.getSlaFirstResponseHours());
    }

    @Transactional(readOnly = true)
    public String describeBusinessHours() {
        final List<WhatsAppBusinessHours> all = businessHoursRepository.findAllByOrderByDayOfWeekAsc();
        final StringBuilder builder = new StringBuilder();
        for (final WhatsAppBusinessHours hours : all) {
            if (!hours.isEnabled()) {
                continue;
            }
            builder.append(dayName(hours.getDayOfWeek())).append(" ").append(hours.getOpenTime()).append("-").append(hours.getCloseTime())
                    .append("; ");
        }
        return builder.toString().trim();
    }

    private LocalDateTime nextBusinessOpen(final LocalDateTime from) {
        LocalDate cursor = from.toLocalDate();
        for (int i = 0; i < 8; i++) {
            final WhatsAppBusinessHours hours = businessHoursRepository.findByDayOfWeek(cursor.getDayOfWeek().getValue()).orElse(null);
            if (hours != null && hours.isEnabled()) {
                final LocalTime open = LocalTime.parse(hours.getOpenTime(), TIME_FORMAT);
                final LocalDateTime candidate = LocalDateTime.of(cursor, open);
                if (!candidate.isBefore(from)) {
                    return candidate;
                }
            }
            cursor = cursor.plusDays(1);
        }
        return DateUtils.getLocalDateTimeOfTenant().plusHours(settings.getSlaFirstResponseHours());
    }

    private String dayName(final int dayOfWeek) {
        return DayOfWeek.of(dayOfWeek).name().substring(0, 3);
    }
}

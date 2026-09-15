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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.apache.fineract.infrastructure.whatsapp.interactive.config.WhatsAppInteractiveProperties;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppBusinessHours;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppBusinessHoursRepository;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppInteractiveSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppBusinessHoursServiceTest {

    @Mock
    private WhatsAppBusinessHoursRepository businessHoursRepository;
    @Mock
    private WhatsAppInteractiveSettingRepository settingRepository;

    private WhatsAppBusinessHoursService service;

    @BeforeEach
    void setUp() {
        final WhatsAppInteractiveProperties properties = new WhatsAppInteractiveProperties();
        final WhatsAppInteractiveSettingsProvider settingsProvider = new WhatsAppInteractiveSettingsProvider(properties, settingRepository);
        service = new WhatsAppBusinessHoursService(businessHoursRepository, settingsProvider);
    }

    @Test
    void detectsWithinBusinessHours() {
        final WhatsAppBusinessHours hours = new WhatsAppBusinessHours();
        hours.setDayOfWeek(1);
        hours.setOpenTime("08:00");
        hours.setCloseTime("17:00");
        hours.setEnabled(true);
        when(businessHoursRepository.findByDayOfWeek(1)).thenReturn(Optional.of(hours));

        assertThat(service.isWithinBusinessHours(LocalDateTime.of(2026, 9, 7, 10, 0))).isTrue();
        assertThat(service.isWithinBusinessHours(LocalDateTime.of(2026, 9, 7, 18, 0))).isFalse();
    }

    @Test
    void calculatesSlaDueAtDuringBusinessHours() {
        final WhatsAppBusinessHours hours = new WhatsAppBusinessHours();
        hours.setDayOfWeek(1);
        hours.setOpenTime("08:00");
        hours.setCloseTime("17:00");
        hours.setEnabled(true);
        when(businessHoursRepository.findByDayOfWeek(1)).thenReturn(Optional.of(hours));

        final LocalDateTime createdAt = LocalDateTime.of(2026, 9, 7, 10, 0);
        assertThat(service.calculateSlaDueAt(createdAt)).isEqualTo(createdAt.plusHours(4));
    }
}

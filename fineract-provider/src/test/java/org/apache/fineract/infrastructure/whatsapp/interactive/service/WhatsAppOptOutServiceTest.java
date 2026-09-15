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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppOptOutEventType;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppOptOutRecord;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppOptOutRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppOptOutServiceTest {

    @Mock
    private WhatsAppOptOutRecordRepository optOutRecordRepository;

    private WhatsAppOptOutService optOutService;

    @BeforeEach
    void setUp() {
        optOutService = new WhatsAppOptOutService(optOutRecordRepository);
    }

    @Test
    void isOptedOutWhenLatestEventIsOptOut() {
        final WhatsAppOptOutRecord record = new WhatsAppOptOutRecord();
        record.setEventType(WhatsAppOptOutEventType.OPT_OUT);
        when(optOutRecordRepository.findLatest("+254712345678")).thenReturn(java.util.Optional.of(record));
        assertTrue(optOutService.isOptedOut("+254712345678"));
    }

    @Test
    void isNotOptedOutWhenLatestEventIsOptIn() {
        final WhatsAppOptOutRecord record = new WhatsAppOptOutRecord();
        record.setEventType(WhatsAppOptOutEventType.OPT_IN);
        when(optOutRecordRepository.findLatest("+254712345678")).thenReturn(java.util.Optional.of(record));
        assertFalse(optOutService.isOptedOut("+254712345678"));
    }
}

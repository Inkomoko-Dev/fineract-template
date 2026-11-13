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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class WhatsAppConversationSearchServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectsUnboundedTextSearch() {
        final WhatsAppConversationSearchService service = new WhatsAppConversationSearchService(jdbcTemplate);
        assertThrows(IllegalArgumentException.class, () -> service.search(null, null, "hello", null, null, null, 10));
    }

    @Test
    void escapesLikeWildcardsWhenBoundByPhone() {
        final WhatsAppConversationSearchService service = new WhatsAppConversationSearchService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class))).thenReturn(List.of());

        service.search("+254700000001", null, "a%b_c", null, null, null, 10);

        final ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(anyString(), params.capture(), any(RowMapper.class));
        assertEquals("%a\\%b\\_c%", params.getValue()[2]);
    }

    @Test
    void allowsTextSearchWithDateWindow() {
        final WhatsAppConversationSearchService service = new WhatsAppConversationSearchService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(Object[].class), any(RowMapper.class))).thenReturn(List.of());

        service.search(null, null, "due", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), null, 20);
        verify(jdbcTemplate).query(anyString(), any(Object[].class), any(RowMapper.class));
    }

    @Test
    void escapeLikeHelperEscapesPercentAndUnderscore() {
        assertEquals("100\\%\\_done", WhatsAppConversationSearchService.escapeLike("100%_done"));
    }
}

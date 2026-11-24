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
package org.apache.fineract.portfolio.creditdecision.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.creditdecision.service.CreditDecisionKillSwitch.SwitchData;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CreditDecisionKillSwitchPrecedenceTest {

    @Test
    @SuppressWarnings("unchecked")
    void globalBeatsCountryAndProduct() {
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final CreditDecisionService profiles = mock(CreditDecisionService.class);
        final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        final CreditDecisionKillSwitch service = new CreditDecisionKillSwitch(jdbc, context, profiles);

        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(RowMapper.class)))
                .thenReturn(List.of(
                        SwitchData.builder().id(1L).scope("PRODUCT").scopeKey("12").enabled(true).build(),
                        SwitchData.builder().id(2L).scope("COUNTRY").scopeKey("KES").enabled(true).build(),
                        SwitchData.builder().id(3L).scope("GLOBAL").scopeKey("*").enabled(true).reason("incident").build()));

        final var hit = service.evaluate("KES", 12L);
        assertTrue(hit.isKilled());
        assertEquals("GLOBAL", hit.getScope());
        assertEquals("incident", hit.getReason());
    }

    @Test
    @SuppressWarnings("unchecked")
    void countryBeatsProductWhenGlobalOff() {
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final CreditDecisionKillSwitch service = new CreditDecisionKillSwitch(jdbc, mock(PlatformSecurityContext.class),
                mock(CreditDecisionService.class));
        when(jdbc.query(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(RowMapper.class)))
                .thenReturn(List.of(
                        SwitchData.builder().id(1L).scope("PRODUCT").scopeKey("12").enabled(true).build(),
                        SwitchData.builder().id(2L).scope("COUNTRY").scopeKey("KES").enabled(true).build(),
                        SwitchData.builder().id(3L).scope("GLOBAL").scopeKey("*").enabled(false).build()));

        final var hit = service.evaluate("KES", 12L);
        assertTrue(hit.isKilled());
        assertEquals("COUNTRY", hit.getScope());
        assertFalse(service.evaluate("UGX", 12L).isKilled() && "COUNTRY".equals(service.evaluate("UGX", 12L).getScope()));
        assertTrue(service.evaluate("UGX", 12L).isKilled());
        assertEquals("PRODUCT", service.evaluate("UGX", 12L).getScope());
    }
}

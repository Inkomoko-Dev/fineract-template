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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CreditDecisionExceptionOverrideGuardTest {

    private JdbcTemplate jdbc;
    private PlatformSecurityContext context;
    private CreditDecisionLoanService loans;
    private CreditDecisionExceptionOverride service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        context = mock(PlatformSecurityContext.class);
        loans = mock(CreditDecisionLoanService.class);
        service = new CreditDecisionExceptionOverride(jdbc, context, loans);
        user = mock(AppUser.class);
        when(context.authenticatedUser()).thenReturn(user);
        when(user.getUsername()).thenReturn("checker");
    }

    @Test
    void requestRejectedWhenLoanNotOnExceptionQueue() {
        when(loans.isOnExceptionQueue(9L)).thenReturn(false);
        final CreditDecisionExceptionOverride.RequestBody body = new CreditDecisionExceptionOverride.RequestBody();
        body.setAction("MANUAL_IC");
        final GeneralPlatformDomainRuleException ex = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.request(9L, body));
        assertTrue(ex.getGlobalisationMessageCode().contains("not.eligible"));
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sameUserCannotApprove() {
        when(user.getUsername()).thenReturn("maker");
        when(jdbc.query(anyString(), any(RowMapper.class), eq(5L))).thenReturn(List.of(
                CreditDecisionExceptionOverride.OverrideData.builder().id(5L).loanId(9L).action("REEVALUATE")
                        .status("PENDING").requestedBy("maker").requestedOn("2026-01-01T00:00:00").build()));
        final GeneralPlatformDomainRuleException ex = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.approve(5L, null));
        assertTrue(ex.getGlobalisationMessageCode().contains("same.user"));
        verify(loans, never()).send(anyLong());
        verify(loans, never()).forceManualIc(anyLong(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void approveRejectedWhenNoLongerOnExceptionQueue() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(5L))).thenReturn(List.of(
                CreditDecisionExceptionOverride.OverrideData.builder().id(5L).loanId(9L).action("MANUAL_IC")
                        .status("PENDING").requestedBy("maker").requestedOn("2026-01-01T00:00:00").build()));
        when(loans.isOnExceptionQueue(9L)).thenReturn(false);
        final GeneralPlatformDomainRuleException ex = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> service.approve(5L, null));
        assertTrue(ex.getGlobalisationMessageCode().contains("not.eligible"));
        verify(loans, never()).forceManualIc(anyLong(), any());
    }
}

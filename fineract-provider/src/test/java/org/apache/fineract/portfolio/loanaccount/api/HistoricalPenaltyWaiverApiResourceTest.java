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
package org.apache.fineract.portfolio.loanaccount.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverData;
import org.apache.fineract.portfolio.loanaccount.service.HistoricalPenaltyWaiverReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

class HistoricalPenaltyWaiverApiResourceTest {

    private final HistoricalPenaltyWaiverReadPlatformService readPlatformService = mock(HistoricalPenaltyWaiverReadPlatformService.class);
    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);

    @BeforeEach
    void setTenant() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
    }

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.clear();
    }

    @SuppressWarnings("unchecked")
    private HistoricalPenaltyWaiverApiResource resourceFor(final AppUser user) {
        when(this.context.authenticatedUser()).thenReturn(user);
        final DefaultToApiJsonSerializer<HistoricalPenaltyWaiverData> waiverSerializer = mock(DefaultToApiJsonSerializer.class);
        when(waiverSerializer.serialize(any())).thenReturn("[]");
        return new HistoricalPenaltyWaiverApiResource(mock(PortfolioCommandSourceWritePlatformService.class),
                (DefaultToApiJsonSerializer<CommandProcessingResult>) mock(DefaultToApiJsonSerializer.class), waiverSerializer,
                this.readPlatformService, this.context);
    }

    @Test
    @DisplayName("A user with read access to everything can see a loan's waiver history")
    void allFunctionsReadUserSeesLoanHistory() {
        final HistoricalPenaltyWaiverApiResource resource = resourceFor(userWith(permission("ALL", "FUNCTIONS_READ")));

        assertDoesNotThrow(() -> resource.retrieveByLoan(432148L));
        verify(this.readPlatformService).retrieveByLoanId(432148L);
    }

    @Test
    @DisplayName("A user with read access to everything can see one waiver and the approval queue")
    void allFunctionsReadUserSeesWaiverAndQueue() {
        final HistoricalPenaltyWaiverApiResource resource = resourceFor(userWith(permission("ALL", "FUNCTIONS_READ")));

        assertDoesNotThrow(() -> resource.retrieveOne(7L));
        assertDoesNotThrow(resource::retrievePendingApprovalQueue);
    }

    @Test
    @DisplayName("A user granted the waiver read permission can see a loan's waiver history")
    void waiverReadUserSeesLoanHistory() {
        final HistoricalPenaltyWaiverApiResource resource = resourceFor(userWith(permission("READ", "HISTORICALPENALTYWAIVER")));

        assertDoesNotThrow(() -> resource.retrieveByLoan(432148L));
        verify(this.readPlatformService).retrieveByLoanId(432148L);
    }

    @Test
    @DisplayName("A user with no read access is still refused the waiver history")
    void userWithoutReadAccessIsRefused() {
        final HistoricalPenaltyWaiverApiResource resource = resourceFor(userWith(permission("READ", "LOAN")));

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveByLoan(432148L));
        assertThrows(NoAuthorizationException.class, () -> resource.retrieveOne(7L));
        assertThrows(NoAuthorizationException.class, resource::retrievePendingApprovalQueue);
        verify(this.readPlatformService, never()).retrieveByLoanId(any());
    }

    private Permission permission(final String action, final String entity) {
        return new Permission("special", entity, action);
    }

    private AppUser userWith(final Permission permission) {
        final Role role = new Role("role-" + permission.getCode(), "role");
        role.updatePermission(permission, true);
        final User springUser = new User("tafese", "password", true, true, true, true,
                List.of(new SimpleGrantedAuthority("DUMMY_ROLE_NOT_USED_OR_PERSISTED_TO_AVOID_EXCEPTION")));
        final Office office = Office.headOffice("Inkomoko - Ethiopia", LocalDate.of(2020, 1, 1), null);
        final AppUser user = new AppUser(office, springUser, Set.of(role), "tafese@example.com", "Tafese", "Etech", null, false, false,
                Collections.emptyList(), false);
        ReflectionTestUtils.setField(user, "lastTimePasswordUpdated", LocalDate.now(ZoneOffset.UTC));
        return user;
    }
}

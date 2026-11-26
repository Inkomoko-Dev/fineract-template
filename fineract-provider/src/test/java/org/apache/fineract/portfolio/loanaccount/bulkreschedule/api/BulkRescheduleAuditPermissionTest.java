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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleAuditRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleExecutionService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleResultExportService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

class BulkRescheduleAuditPermissionTest {

    private static final Long EXECUTION_ID = 5L;
    private static final Long OFFICE_ID = 1L;

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final BulkRescheduleExecutionRepository executionRepository = mock(BulkRescheduleExecutionRepository.class);
    private final BulkRescheduleAuditRepository auditRepository = mock(BulkRescheduleAuditRepository.class);
    private final OfficeHierarchyService officeHierarchyService = mock(OfficeHierarchyService.class);
    private AppUser requester;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        this.requester = userWith(11L, permission("CREATE", "RESCHEDULELOAN"));
        final BulkRescheduleExecution execution = new BulkRescheduleExecution();
        ReflectionTestUtils.setField(execution, "id", EXECUTION_ID);
        execution.setUser(this.requester);
        execution.setOfficeId(OFFICE_ID);
        when(this.executionRepository.findById(EXECUTION_ID)).thenReturn(Optional.of(execution));
        when(this.officeHierarchyService.validateUserAccessToOffice(any(), anyLong())).thenReturn(true);
        when(this.auditRepository.findByExecutionIdOrdered(EXECUTION_ID)).thenReturn(Collections.emptyList());
    }

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    @DisplayName("An approver without the audit permission can see the history of a request they can open")
    void approverWithoutAuditPermissionSeesHistory() {
        assertDoesNotThrow(() -> resourceFor(userWith(22L, permission("APPROVE", "RESCHEDULELOAN"))).getAuditHistory(EXECUTION_ID));
        verify(this.auditRepository).findByExecutionIdOrdered(EXECUTION_ID);
    }

    @Test
    @DisplayName("A reschedule reader without the audit permission can see the history")
    void rescheduleReaderSeesHistory() {
        assertDoesNotThrow(() -> resourceFor(userWith(22L, permission("READ", "RESCHEDULELOAN"))).getAuditHistory(EXECUTION_ID));
    }

    @Test
    @DisplayName("A user with read access to everything can open the request and see its history")
    void allFunctionsReadUserSeesRequestAndHistory() {
        final BulkRescheduleApiResource resource = resourceFor(userWith(22L, permission("ALL", "FUNCTIONS_READ")));

        assertDoesNotThrow(() -> resource.getAuditHistory(EXECUTION_ID));
        assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(resource, "validateCanViewExecution",
                this.context.authenticatedUser(), this.executionRepository.findById(EXECUTION_ID).orElseThrow()));
    }

    @Test
    @DisplayName("The requester can see the history of their own request")
    void requesterSeesHistory() {
        assertDoesNotThrow(() -> resourceFor(this.requester).getAuditHistory(EXECUTION_ID));
    }

    @Test
    @DisplayName("An auditor with only the audit permission can still see the history")
    void auditReaderSeesHistory() {
        assertDoesNotThrow(() -> resourceFor(userWith(22L, permission("READ", "AUDIT"))).getAuditHistory(EXECUTION_ID));
    }

    @Test
    @DisplayName("A user who can neither open the request nor read audits is refused")
    void unrelatedUserIsRefused() {
        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> resourceFor(userWith(22L, permission("READ", "LOAN"))).getAuditHistory(EXECUTION_ID));
        verify(this.auditRepository, never()).findByExecutionIdOrdered(any());
    }

    @Test
    @DisplayName("A viewer outside the request's office is refused")
    void viewerOutsideOfficeIsRefused() {
        when(this.officeHierarchyService.validateUserAccessToOffice(any(), anyLong())).thenReturn(false);

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> resourceFor(userWith(22L, permission("APPROVE", "RESCHEDULELOAN"))).getAuditHistory(EXECUTION_ID));
    }

    @SuppressWarnings("unchecked")
    private BulkRescheduleApiResource resourceFor(final AppUser user) {
        when(this.context.authenticatedUser()).thenReturn(user);
        return new BulkRescheduleApiResource((DefaultToApiJsonSerializer<Object>) mock(DefaultToApiJsonSerializer.class),
                mock(DefaultToApiJsonSerializer.class), this.context, new ApiRequestParameterHelper(),
                mock(PortfolioCommandSourceWritePlatformService.class), mock(BulkRescheduleService.class),
                mock(BulkRescheduleExecutionService.class), this.executionRepository, mock(BulkRescheduleResultRepository.class),
                mock(OfficeRepository.class), this.officeHierarchyService, mock(AppUserRepository.class), this.auditRepository,
                mock(BulkRescheduleResultExportService.class));
    }

    private static Permission permission(final String action, final String entity) {
        return new Permission("special", entity, action);
    }

    private static AppUser userWith(final Long id, final Permission... permissions) {
        final Role role = new Role("role-" + id, "role");
        Arrays.stream(permissions).forEach(permission -> role.updatePermission(permission, true));
        final User springUser = new User("user" + id, "password", true, true, true, true,
                List.of(new SimpleGrantedAuthority("DUMMY_ROLE_NOT_USED_OR_PERSISTED_TO_AVOID_EXCEPTION")));
        final Office office = Office.headOffice("Inkomoko - Rwanda", LocalDate.of(2020, 1, 1), null);
        final AppUser user = new AppUser(office, springUser, Set.of(role), "user" + id + "@example.com", "Bulk", "Viewer", null, false,
                false, Collections.emptyList(), false);
        ReflectionTestUtils.setField(user, "lastTimePasswordUpdated", LocalDate.now(ZoneOffset.UTC));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}

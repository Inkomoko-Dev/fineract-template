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
package org.apache.fineract.portfolio.loanclassification.api;

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
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanclassification.service.LoanClassificationReadPlatformService;
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

class LoanClassificationApiResourceTest {

    private final LoanClassificationReadPlatformService readPlatformService = mock(LoanClassificationReadPlatformService.class);
    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final UriInfo uriInfo = mock(UriInfo.class);

    @BeforeEach
    void setTenant() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        when(this.uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    }

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.clear();
    }

    @SuppressWarnings("unchecked")
    private LoanClassificationApiResource resourceFor(final AppUser user) {
        when(this.context.authenticatedUser()).thenReturn(user);
        return new LoanClassificationApiResource(this.context, this.readPlatformService, new ApiRequestParameterHelper(),
                (DefaultToApiJsonSerializer<Object>) mock(DefaultToApiJsonSerializer.class),
                mock(PortfolioCommandSourceWritePlatformService.class));
    }

    @Test
    @DisplayName("A user with read access to everything can see a loan's classification and its history")
    void allFunctionsReadUserSeesClassification() {
        final LoanClassificationApiResource resource = resourceFor(userWith(permission("ALL", "FUNCTIONS_READ")));

        assertDoesNotThrow(() -> resource.retrieve(432148L, this.uriInfo));
        assertDoesNotThrow(() -> resource.retrieveAudit(432148L, this.uriInfo));
        verify(this.readPlatformService).retrieveLoanClassification(432148L);
        verify(this.readPlatformService).retrieveLoanAudit(432148L);
    }

    @Test
    @DisplayName("A user granted the classification read permission can see a loan's classification")
    void classificationReadUserSeesClassification() {
        final LoanClassificationApiResource resource = resourceFor(userWith(permission("READ", "LOANCLASSIFICATION")));

        assertDoesNotThrow(() -> resource.retrieve(432148L, this.uriInfo));
        assertDoesNotThrow(() -> resource.retrieveAudit(432148L, this.uriInfo));
    }

    @Test
    @DisplayName("A user with no classification read access is still refused")
    void userWithoutReadAccessIsRefused() {
        final LoanClassificationApiResource resource = resourceFor(userWith(permission("READ", "LOAN")));

        assertThrows(NoAuthorizationException.class, () -> resource.retrieve(432148L, this.uriInfo));
        assertThrows(NoAuthorizationException.class, () -> resource.retrieveAudit(432148L, this.uriInfo));
        verify(this.readPlatformService, never()).retrieveLoanClassification(any());
        verify(this.readPlatformService, never()).retrieveLoanAudit(any());
    }

    private Permission permission(final String action, final String entity) {
        return new Permission("special", entity, action);
    }

    private AppUser userWith(final Permission permission) {
        final Role role = new Role("role-" + permission.getCode(), "role");
        role.updatePermission(permission, true);
        final User springUser = new User("auditor", "password", true, true, true, true,
                List.of(new SimpleGrantedAuthority("DUMMY_ROLE_NOT_USED_OR_PERSISTED_TO_AVOID_EXCEPTION")));
        final Office office = Office.headOffice("Inkomoko - Rwanda", LocalDate.of(2020, 1, 1), null);
        final AppUser user = new AppUser(office, springUser, Set.of(role), "auditor@example.com", "Bdo", "Auditor", null, false, false,
                Collections.emptyList(), false);
        ReflectionTestUtils.setField(user, "lastTimePasswordUpdated", LocalDate.now(ZoneOffset.UTC));
        return user;
    }
}

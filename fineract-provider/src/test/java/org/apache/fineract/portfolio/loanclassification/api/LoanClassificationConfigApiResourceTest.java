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

class LoanClassificationConfigApiResourceTest {

    private final LoanClassificationReadPlatformService readPlatformService = mock(LoanClassificationReadPlatformService.class);
    private final PortfolioCommandSourceWritePlatformService commands = mock(PortfolioCommandSourceWritePlatformService.class);
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

    @Test
    @DisplayName("A user with read access to everything can open the loan classification setup")
    void allFunctionsReadUserSeesSetup() {
        final LoanClassificationConfigApiResource resource = resourceFor(userWith(permission("ALL", "FUNCTIONS_READ")));

        assertDoesNotThrow(() -> resource.retrieveCodes(this.uriInfo));
        assertDoesNotThrow(() -> resource.retrieveTemplate(this.uriInfo));
        assertDoesNotThrow(() -> resource.retrieveCountries(this.uriInfo));
        assertDoesNotThrow(() -> resource.retrieveCountry(3L, this.uriInfo));
        assertDoesNotThrow(() -> resource.retrieveSummary(null, null, null, null, null, this.uriInfo));
        verify(this.readPlatformService).retrieveAllCountryConfigs();
        verify(this.readPlatformService).retrieveCountryConfig(3L);
    }

    @Test
    @DisplayName("A user granted the setup read permission can open the loan classification setup")
    void setupReaderSeesSetup() {
        final LoanClassificationConfigApiResource resource = resourceFor(userWith(permission("READ", "LOANCLASSIFICATIONCONFIG")));

        assertDoesNotThrow(() -> resource.retrieveCountries(this.uriInfo));
        assertDoesNotThrow(() -> resource.retrieveSummary(null, null, null, null, null, this.uriInfo));
    }

    @Test
    @DisplayName("A user with no setup read access is still refused")
    void userWithoutReadAccessIsRefused() {
        final LoanClassificationConfigApiResource resource = resourceFor(userWith(permission("READ", "LOAN")));

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveCodes(this.uriInfo));
        assertThrows(NoAuthorizationException.class, () -> resource.retrieveTemplate(this.uriInfo));
        assertThrows(NoAuthorizationException.class, () -> resource.retrieveCountries(this.uriInfo));
        assertThrows(NoAuthorizationException.class, () -> resource.retrieveCountry(3L, this.uriInfo));
        assertThrows(NoAuthorizationException.class, () -> resource.retrieveSummary(null, null, null, null, null, this.uriInfo));
        verify(this.readPlatformService, never()).retrieveAllCountryConfigs();
    }

    @Test
    @DisplayName("Read access to everything still cannot change the loan classification setup")
    void allFunctionsReadUserCannotChangeSetup() {
        final LoanClassificationConfigApiResource resource = resourceFor(userWith(permission("ALL", "FUNCTIONS_READ")));

        assertThrows(NoAuthorizationException.class, () -> resource.createCountryConfig("{}"));
        assertThrows(NoAuthorizationException.class, () -> resource.updateCountryConfig(3L, "{}"));
        assertThrows(NoAuthorizationException.class, () -> resource.deleteCountryConfig(3L));
        verify(this.commands, never()).logCommandSource(any());
    }

    @SuppressWarnings("unchecked")
    private LoanClassificationConfigApiResource resourceFor(final AppUser user) {
        when(this.context.authenticatedUser()).thenReturn(user);
        return new LoanClassificationConfigApiResource(this.context, this.readPlatformService, new ApiRequestParameterHelper(),
                (DefaultToApiJsonSerializer<Object>) mock(DefaultToApiJsonSerializer.class), this.commands);
    }

    private Permission permission(final String action, final String entity) {
        return new Permission("organisation", entity, action);
    }

    private AppUser userWith(final Permission permission) {
        final Role role = new Role("role-" + permission.getCode(), "role");
        role.updatePermission(permission, true);
        final User springUser = new User("viewer", "password", true, true, true, true,
                List.of(new SimpleGrantedAuthority("DUMMY_ROLE_NOT_USED_OR_PERSISTED_TO_AVOID_EXCEPTION")));
        final Office office = Office.headOffice("Inkomoko - Rwanda", LocalDate.of(2020, 1, 1), null);
        final AppUser user = new AppUser(office, springUser, Set.of(role), "viewer@example.com", "Bdo", "Auditor", null, false, false,
                Collections.emptyList(), false);
        ReflectionTestUtils.setField(user, "lastTimePasswordUpdated", LocalDate.now(ZoneOffset.UTC));
        return user;
    }
}

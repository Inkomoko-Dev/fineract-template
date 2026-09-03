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
package org.apache.fineract.infrastructure.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeAccessScope;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

public class SpringSecurityPlatformSecurityContextOfficeScopeTest {

    private ConfigurationDomainService configurationDomainService;
    private SpringSecurityPlatformSecurityContext context;
    private AppUser user;
    private Office kigali;

    @BeforeEach
    public void setUp() {
        this.configurationDomainService = mock(ConfigurationDomainService.class);
        when(this.configurationDomainService.isPasswordForcedResetEnable()).thenReturn(false);
        this.context = new SpringSecurityPlatformSecurityContext(this.configurationDomainService);

        this.kigali = office(2L, ".1.2.");
        this.user = mock(AppUser.class);
        when(this.user.getOffice()).thenReturn(this.kigali);
        when(this.user.getPasswordNeverExpires()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(this.user, null, List.of()));
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("The office scope is resolved from the user with strict scoping switched off by default")
    public void officeScopeUsesTheLenientConfiguration() {
        when(this.configurationDomainService.isStrictOfficeScopeEnabled()).thenReturn(false);
        when(this.user.officeAccessScope(false)).thenReturn(OfficeAccessScope.hierarchical(List.of(".1.2.")));

        assertEquals(List.of(".1.2."), this.context.officeAccessScope().getHierarchies());
    }

    @Test
    @DisplayName("Strict office scoping is passed through to the user")
    public void officeScopeUsesTheStrictConfiguration() {
        when(this.configurationDomainService.isStrictOfficeScopeEnabled()).thenReturn(true);
        when(this.user.officeAccessScope(true)).thenReturn(OfficeAccessScope.exact(List.of(".1.2.")));

        assertEquals(List.of(".1.2."), this.context.officeAccessScope().getHierarchies());
        assertFalse(this.context.officeAccessScope().isIncludeDescendants());
    }

    @Test
    @DisplayName("Access to a resource in any assigned office is allowed")
    public void accessIsGrantedForEveryAssignedOffice() {
        when(this.configurationDomainService.isStrictOfficeScopeEnabled()).thenReturn(false);
        when(this.user.officeAccessScope(false)).thenReturn(OfficeAccessScope.hierarchical(Arrays.asList(".1.2.5.", ".1.3.")));

        assertDoesNotThrow(() -> this.context.validateAccessRights(".1.2.5."));
        assertDoesNotThrow(() -> this.context.validateAccessRights(".1.2.5.9."));
        assertDoesNotThrow(() -> this.context.validateAccessRights(".1.3."));
    }

    @Test
    @DisplayName("Access to a resource outside every assigned office is refused")
    public void accessIsRefusedOutsideTheAssignedOffices() {
        when(this.configurationDomainService.isStrictOfficeScopeEnabled()).thenReturn(false);
        when(this.user.officeAccessScope(false)).thenReturn(OfficeAccessScope.hierarchical(Arrays.asList(".1.2.5.", ".1.3.")));

        assertThrows(NoAuthorizationException.class, () -> this.context.validateAccessRights(".1.2."));
        assertThrows(NoAuthorizationException.class, () -> this.context.validateAccessRights(".1.4."));
    }

    @Test
    @DisplayName("The single valued office hierarchy still answers with the home office")
    public void officeHierarchyRemainsTheHomeOffice() {
        assertEquals(".1.2.", this.context.officeHierarchy());
    }

    private Office office(final Long id, final String hierarchy) {
        final Office office = Office.headOffice("office-" + id, java.time.LocalDate.of(2020, 1, 1), null);
        ReflectionTestUtils.setField(office, "id", id);
        ReflectionTestUtils.setField(office, "hierarchy", hierarchy);
        return office;
    }
}

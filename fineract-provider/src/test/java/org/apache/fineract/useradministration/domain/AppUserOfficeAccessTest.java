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
package org.apache.fineract.useradministration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeAccessScope;
import org.apache.fineract.useradministration.service.AppUserConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

public class AppUserOfficeAccessTest {

    private static final boolean STRICT = true;
    private static final boolean LENIENT = false;

    private Office kigali;
    private Office kigaliB;
    private Office kigaliC;
    private Office nairobi;

    @BeforeEach
    public void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        this.kigali = office(2L, ".1.2.");
        this.kigaliB = office(5L, ".1.2.5.");
        this.kigaliC = office(6L, ".1.2.6.");
        this.nairobi = office(3L, ".1.3.");
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    // Scenario 1 - staff assigned to a parent location
    @Test
    @DisplayName("A user on a parent office with the permission sees every child office")
    public void parentOfficeUserWithPermissionSeesChildOffices() {
        final AppUser user = userIn(this.kigali, roleWithHierarchicalAccess());

        assertTrue(user.hasHierarchicalOfficeAccess());

        final OfficeAccessScope scope = user.officeAccessScope(STRICT);
        assertTrue(scope.isIncludeDescendants());
        assertEquals(List.of(".1.2."), scope.getHierarchies());
        assertTrue(scope.covers(".1.2.5."));
        assertTrue(scope.covers(".1.2.6."));
        assertFalse(scope.covers(".1.3."));
    }

    @Test
    @DisplayName("ALL_FUNCTIONS carries hierarchical office access")
    public void allFunctionsGrantsHierarchicalAccess() {
        final AppUser user = userIn(this.kigali, roleWith("authorisation", "FUNCTIONS", "ALL"));

        assertTrue(user.hasHierarchicalOfficeAccess());
    }

    // Scenario 2 - staff assigned to multiple locations
    @Test
    @DisplayName("A user with the permission sees each additionally assigned office and its children")
    public void multiOfficeUserWithPermissionSeesEveryAssignedOffice() {
        final AppUser user = userIn(this.nairobi, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(Arrays.asList(this.kigaliB, this.kigaliC));

        final OfficeAccessScope scope = user.officeAccessScope(STRICT);

        assertEquals(Arrays.asList(".1.2.5.", ".1.2.6.", ".1.3."), scope.getHierarchies());
        assertTrue(scope.covers(".1.2.5."));
        assertTrue(scope.covers(".1.2.6.9."));
        assertTrue(scope.covers(".1.3.4."));
        assertFalse(scope.covers(".1.2."));
    }

    @Test
    @DisplayName("An additional office that is already under the home office adds nothing")
    public void additionalOfficeUnderHomeOfficeIsCollapsed() {
        final AppUser user = userIn(this.kigali, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(List.of(this.kigaliB));

        assertEquals(List.of(".1.2."), user.officeAccessScope(STRICT).getHierarchies());
    }

    // Scenario 3 - restricted staff
    @Test
    @DisplayName("Without the permission a user under strict scoping sees only their own office")
    public void restrictedUserUnderStrictScopingSeesOnlyTheirOwnOffice() {
        final AppUser user = userIn(this.kigali, roleWith("portfolio", "CLIENT", "READ"));

        assertFalse(user.hasHierarchicalOfficeAccess());

        final OfficeAccessScope scope = user.officeAccessScope(STRICT);
        assertFalse(scope.isIncludeDescendants());
        assertEquals(List.of(".1.2."), scope.getHierarchies());
        assertTrue(scope.covers(".1.2."));
        assertFalse(scope.covers(".1.2.5."));
    }

    @Test
    @DisplayName("Without the permission additional office assignments are ignored")
    public void restrictedUserDoesNotInheritAdditionalOffices() {
        final AppUser user = userIn(this.kigali, roleWith("portfolio", "CLIENT", "READ"));
        user.updateAdditionalOffices(Arrays.asList(this.kigaliB, this.nairobi));

        assertEquals(List.of(".1.2."), user.officeAccessScope(STRICT).getHierarchies());
        assertEquals(List.of(".1.2."), user.officeAccessScope(LENIENT).getHierarchies());
    }

    @Test
    @DisplayName("With strict scoping switched off a user without the permission keeps the existing hierarchy behaviour")
    public void restrictedUserKeepsLegacyHierarchyWhenStrictScopingIsOff() {
        final AppUser user = userIn(this.kigali, roleWith("portfolio", "CLIENT", "READ"));

        final OfficeAccessScope scope = user.officeAccessScope(LENIENT);

        assertTrue(scope.isIncludeDescendants());
        assertTrue(scope.covers(".1.2.5."));
    }

    // Scenario 5 - audit of assignment changes
    @Test
    @DisplayName("Changing the assigned offices records a before and after entry for the audit trail")
    public void changingAssignedOfficesIsRecordedForAudit() {
        final AppUser user = userIn(this.nairobi, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(List.of(this.kigaliB));

        final Map<String, Object> changes = user.updateAdditionalOfficesWithChanges(Arrays.asList(this.kigaliB, this.kigaliC));

        assertEquals(1, changes.size());
        final Map<String, Object> beforeAfter = asMap(changes.get(AppUserConstants.OFFICE_IDS));
        assertEquals(List.of(5L), beforeAfter.get("before"));
        assertEquals(Arrays.asList(5L, 6L), beforeAfter.get("after"));
    }

    @Test
    @DisplayName("Re-assigning the same offices records no audit change")
    public void reassigningTheSameOfficesRecordsNothing() {
        final AppUser user = userIn(this.nairobi, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(Arrays.asList(this.kigaliC, this.kigaliB));

        assertTrue(user.updateAdditionalOfficesWithChanges(Arrays.asList(this.kigaliB, this.kigaliC)).isEmpty());
    }

    @Test
    @DisplayName("Clearing the additional offices is recorded and takes effect")
    public void clearingAdditionalOfficesIsRecorded() {
        final AppUser user = userIn(this.nairobi, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(List.of(this.kigaliB));

        final Map<String, Object> changes = user.updateAdditionalOfficesWithChanges(Collections.emptyList());

        assertEquals(List.of(), asMap(changes.get(AppUserConstants.OFFICE_IDS)).get("after"));
        assertEquals(List.of(".1.3."), user.officeAccessScope(STRICT).getHierarchies());
    }

    @Test
    @DisplayName("The home office is not duplicated by an additional assignment to itself")
    public void homeOfficeIsAlwaysInScope() {
        final AppUser user = userIn(this.nairobi, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(List.of(this.nairobi));

        assertEquals(List.of(".1.3."), user.officeAccessScope(STRICT).getHierarchies());
        assertEquals(List.of(this.nairobi), user.getAdditionalOffices());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(final Object value) {
        return (Map<String, Object>) value;
    }

    private Office office(final Long id, final String hierarchy) {
        final Office office = Office.headOffice("office-" + id, LocalDate.of(2020, 1, 1), null);
        ReflectionTestUtils.setField(office, "id", id);
        ReflectionTestUtils.setField(office, "hierarchy", hierarchy);
        return office;
    }

    private Role roleWithHierarchicalAccess() {
        return roleWith("authorisation", AppUserConstants.OFFICE_ACCESS_ENTITY, AppUserConstants.HIERARCHICAL_ACTION);
    }

    private Role roleWith(final String grouping, final String entity, final String action) {
        final Role role = new Role("role-" + action + "-" + entity, "role");
        role.updatePermission(new Permission(grouping, entity, action), true);
        return role;
    }

    private AppUser userIn(final Office office, final Role... roles) {
        final Set<Role> roleSet = new HashSet<>(Arrays.asList(roles));
        final User springUser = new User("jdoe", "password", true, true, true, true,
                List.of(new SimpleGrantedAuthority("DUMMY_ROLE_NOT_USED_OR_PERSISTED_TO_AVOID_EXCEPTION")));
        final AppUser user = new AppUser(office, springUser, roleSet, "jdoe@example.com", "Jane", "Doe", null, false, false,
                Collections.emptyList(), false);
        ReflectionTestUtils.setField(user, "lastTimePasswordUpdated", LocalDate.now());
        return user;
    }
}

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

    @Test
    @DisplayName("Scenario 1: strict scoping, a parent office user with the permission sees every child office")
    public void parentOfficeUserWithPermissionSeesChildOffices() {
        final AppUser user = userIn(this.kigali, roleWithHierarchicalAccess());

        final OfficeAccessScope scope = user.officeAccessScope(STRICT);

        assertTrue(user.hasHierarchicalOfficeAccess());
        assertTrue(scope.isIncludeDescendants());
        assertEquals(List.of(".1.2."), scope.getHierarchies());
        assertTrue(scope.covers(".1.2.5."));
        assertTrue(scope.covers(".1.2.6."));
        assertFalse(scope.covers(".1.3."));
    }

    @Test
    @DisplayName("Scenario 1: ALL_FUNCTIONS carries hierarchical office access")
    public void allFunctionsGrantsHierarchicalAccess() {
        final AppUser user = userIn(this.kigali, roleWith("authorisation", "FUNCTIONS", "ALL"));

        assertTrue(user.hasHierarchicalOfficeAccess());
        assertTrue(user.officeAccessScope(STRICT).covers(".1.2.5."));
    }

    @Test
    @DisplayName("Scenario 2: assigned offices apply without any permission, strict scoping off, with their children")
    public void assignedOfficesApplyWithoutPermissionWhenStrictScopingIsOff() {
        final AppUser user = userIn(this.kigaliB, roleWith("portfolio", "CLIENT", "READ"));
        user.updateAdditionalOffices(List.of(this.nairobi));

        final OfficeAccessScope scope = user.officeAccessScope(LENIENT);

        assertTrue(scope.isIncludeDescendants());
        assertEquals(Arrays.asList(".1.2.5.", ".1.3."), scope.getHierarchies());
        assertTrue(scope.covers(".1.2.5.9."));
        assertTrue(scope.covers(".1.3.4."));
        assertFalse(scope.covers(".1.2.6."));
    }

    @Test
    @DisplayName("Scenario 2: strict scoping, without the permission a multi-office user sees exactly the assigned offices")
    public void multiOfficeUserWithoutPermissionSeesExactlyTheAssignedOffices() {
        final AppUser user = userIn(this.kigaliB, roleWith("portfolio", "CLIENT", "READ"));
        user.updateAdditionalOffices(List.of(this.kigaliC));

        final OfficeAccessScope scope = user.officeAccessScope(STRICT);

        assertFalse(scope.isIncludeDescendants());
        assertEquals(Arrays.asList(".1.2.5.", ".1.2.6."), scope.getHierarchies());
        assertTrue(scope.covers(".1.2.6."));
        assertFalse(scope.covers(".1.2.6.9."));
        assertFalse(scope.covers(".1.2."));
    }

    @Test
    @DisplayName("Scenario 2: strict scoping, with the permission a multi-office user sees each assigned office and its children")
    public void multiOfficeUserWithPermissionSeesEveryAssignedTree() {
        final AppUser user = userIn(this.nairobi, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(Arrays.asList(this.kigaliB, this.kigaliC));

        final OfficeAccessScope scope = user.officeAccessScope(STRICT);

        assertEquals(Arrays.asList(".1.2.5.", ".1.2.6.", ".1.3."), scope.getHierarchies());
        assertTrue(scope.covers(".1.2.6.9."));
        assertTrue(scope.covers(".1.3.4."));
        assertFalse(scope.covers(".1.2."));
    }

    @Test
    @DisplayName("Scenario 2: an assigned office already beneath the home office adds nothing")
    public void assignedOfficeUnderHomeOfficeIsCollapsed() {
        final AppUser user = userIn(this.kigali, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(List.of(this.kigaliB));

        assertEquals(List.of(".1.2."), user.officeAccessScope(STRICT).getHierarchies());
    }

    @Test
    @DisplayName("Scenario 3: strict scoping, without the permission a single-office user sees only that office")
    public void restrictedUserUnderStrictScopingSeesOnlyTheirOwnOffice() {
        final AppUser user = userIn(this.kigali, roleWith("portfolio", "CLIENT", "READ"));

        final OfficeAccessScope scope = user.officeAccessScope(STRICT);

        assertFalse(user.hasHierarchicalOfficeAccess());
        assertFalse(scope.isIncludeDescendants());
        assertEquals(List.of(".1.2."), scope.getHierarchies());
        assertTrue(scope.covers(".1.2."));
        assertFalse(scope.covers(".1.2.5."));
    }

    @Test
    @DisplayName("Strict scoping off: a single-office user without the permission keeps today's downward visibility")
    public void restrictedUserKeepsTodaysVisibilityWhenStrictScopingIsOff() {
        final AppUser user = userIn(this.kigali, roleWith("portfolio", "CLIENT", "READ"));

        final OfficeAccessScope scope = user.officeAccessScope(LENIENT);

        assertTrue(scope.isIncludeDescendants());
        assertEquals(List.of(".1.2."), scope.getHierarchies());
        assertTrue(scope.covers(".1.2.5."));
        assertFalse(scope.covers(".1.3."));
    }

    @Test
    @DisplayName("Scenario 5: changing the assigned offices records before and after for the audit trail")
    public void changingAssignedOfficesIsRecordedForAudit() {
        final AppUser user = userIn(this.nairobi, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(List.of(this.kigaliB));

        final Map<String, Object> changes = user.updateAdditionalOfficesWithChanges(Arrays.asList(this.kigaliC, this.kigaliB));

        assertEquals(1, changes.size());
        final Map<String, Object> beforeAfter = asMap(changes.get(AppUserConstants.OFFICE_IDS));
        assertEquals(List.of(5L), beforeAfter.get("before"));
        assertEquals(Arrays.asList(5L, 6L), beforeAfter.get("after"));
    }

    @Test
    @DisplayName("Scenario 5: re-assigning the same offices records no audit change")
    public void reassigningTheSameOfficesRecordsNothing() {
        final AppUser user = userIn(this.nairobi, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(Arrays.asList(this.kigaliC, this.kigaliB));

        assertTrue(user.updateAdditionalOfficesWithChanges(Arrays.asList(this.kigaliB, this.kigaliC)).isEmpty());
    }

    @Test
    @DisplayName("Scenario 5: clearing the assigned offices is recorded and takes effect")
    public void clearingAssignedOfficesIsRecorded() {
        final AppUser user = userIn(this.nairobi, roleWithHierarchicalAccess());
        user.updateAdditionalOffices(List.of(this.kigaliB));

        final Map<String, Object> changes = user.updateAdditionalOfficesWithChanges(Collections.emptyList());

        assertEquals(List.of(), asMap(changes.get(AppUserConstants.OFFICE_IDS)).get("after"));
        assertEquals(List.of(".1.3."), user.officeAccessScope(STRICT).getHierarchies());
        assertTrue(user.getAdditionalOffices().isEmpty());
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

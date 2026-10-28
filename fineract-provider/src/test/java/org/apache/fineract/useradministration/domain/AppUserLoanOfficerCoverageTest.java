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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.useradministration.service.AppUserConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

public class AppUserLoanOfficerCoverageTest {

    private static final boolean STRICT = true;
    private static final boolean LENIENT = false;

    private Office ethiopia;
    private Office assosa;
    private Office kakuma;

    @BeforeEach
    public void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        this.ethiopia = office(5L, ".5.");
        this.assosa = office(8L, ".5.8.");
        this.kakuma = office(77L, ".121.3.77.");
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    @DisplayName("A loan officer whose login is assigned Assosa can be the loan officer for an Assosa client")
    public void loanOfficerAssignedTheClientsLocationCanServeThere() {
        final AppUser user = userIn(this.kakuma, loanOfficerAt(this.kakuma));
        user.updateAdditionalOffices(List.of(this.assosa));

        assertTrue(user.canServeAsLoanOfficerFor(this.assosa.getHierarchy(), LENIENT));
    }

    @Test
    @DisplayName("A login assigned Assosa but not linked to a staff record offers no loan officer")
    public void loginWithoutStaffRecordCannotServe() {
        final AppUser user = userIn(this.kakuma, null);
        user.updateAdditionalOffices(List.of(this.assosa));

        assertFalse(user.canServeAsLoanOfficerFor(this.assosa.getHierarchy(), LENIENT));
    }

    @Test
    @DisplayName("A login assigned Assosa whose staff record is not a loan officer offers no loan officer")
    public void staffWhoIsNotALoanOfficerCannotServe() {
        final Staff staff = loanOfficerAt(this.kakuma);
        ReflectionTestUtils.setField(staff, "loanOfficer", false);
        final AppUser user = userIn(this.kakuma, staff);
        user.updateAdditionalOffices(List.of(this.assosa));

        assertFalse(user.canServeAsLoanOfficerFor(this.assosa.getHierarchy(), LENIENT));
    }

    @Test
    @DisplayName("A login assigned Assosa whose loan officer record is inactive offers no loan officer")
    public void inactiveLoanOfficerCannotServe() {
        final Staff staff = loanOfficerAt(this.kakuma);
        ReflectionTestUtils.setField(staff, "active", false);
        final AppUser user = userIn(this.kakuma, staff);
        user.updateAdditionalOffices(List.of(this.assosa));

        assertFalse(user.canServeAsLoanOfficerFor(this.assosa.getHierarchy(), LENIENT));
    }

    @Test
    @DisplayName("A loan officer whose login is not assigned Assosa is not offered for an Assosa client")
    public void loanOfficerNotAssignedTheClientsLocationCannotServeThere() {
        final AppUser user = userIn(this.kakuma, loanOfficerAt(this.kakuma));

        assertFalse(user.canServeAsLoanOfficerFor(this.assosa.getHierarchy(), LENIENT));
    }

    @Test
    @DisplayName("Scenario 1: a parent-location loan officer with hierarchical access serves child-location clients")
    public void parentLocationLoanOfficerWithHierarchicalAccessServesChildLocation() {
        final AppUser user = userIn(this.ethiopia, loanOfficerAt(this.ethiopia), hierarchicalAccess());

        assertTrue(user.canServeAsLoanOfficerFor(this.assosa.getHierarchy(), STRICT));
    }

    @Test
    @DisplayName("Scenario 3: under strict scoping a parent-location loan officer without the permission serves only their own location")
    public void restrictedParentLocationLoanOfficerDoesNotServeChildLocation() {
        final AppUser user = userIn(this.ethiopia, loanOfficerAt(this.ethiopia));

        assertFalse(user.canServeAsLoanOfficerFor(this.assosa.getHierarchy(), STRICT));
        assertTrue(user.canServeAsLoanOfficerFor(this.ethiopia.getHierarchy(), STRICT));
    }

    @Test
    @DisplayName("Scenario 3: under strict scoping a loan officer explicitly assigned Assosa still serves Assosa")
    public void explicitlyAssignedLoanOfficerServesUnderStrictScoping() {
        final AppUser user = userIn(this.kakuma, loanOfficerAt(this.kakuma));
        user.updateAdditionalOffices(List.of(this.assosa));

        assertTrue(user.canServeAsLoanOfficerFor(this.assosa.getHierarchy(), STRICT));
    }

    private Staff loanOfficerAt(final Office office) {
        final Staff staff = BeanUtils.instantiateClass(Staff.class);
        ReflectionTestUtils.setField(staff, "office", office);
        ReflectionTestUtils.setField(staff, "loanOfficer", true);
        ReflectionTestUtils.setField(staff, "active", true);
        return staff;
    }

    private Office office(final Long id, final String hierarchy) {
        final Office office = Office.headOffice("office-" + id, LocalDate.of(2020, 1, 1), null);
        ReflectionTestUtils.setField(office, "id", id);
        ReflectionTestUtils.setField(office, "hierarchy", hierarchy);
        return office;
    }

    private Role role() {
        final Role role = new Role("role-loan-officer", "role");
        role.updatePermission(new Permission("portfolio", "LOAN", "READ"), true);
        return role;
    }

    private Role hierarchicalAccess() {
        final Role role = new Role("role-hierarchical", "role");
        role.updatePermission(new Permission("authorisation", AppUserConstants.OFFICE_ACCESS_ENTITY, AppUserConstants.HIERARCHICAL_ACTION),
                true);
        return role;
    }

    private AppUser userIn(final Office office, final Staff staff, final Role... extraRoles) {
        final Set<Role> roleSet = new HashSet<>(Arrays.asList(extraRoles));
        roleSet.add(role());
        final User springUser = new User("jdoe", "password", true, true, true, true,
                List.of(new SimpleGrantedAuthority("DUMMY_ROLE_NOT_USED_OR_PERSISTED_TO_AVOID_EXCEPTION")));
        final AppUser user = new AppUser(office, springUser, roleSet, "jdoe@example.com", "Jane", "Doe", staff, false, false,
                Collections.emptyList(), false);
        ReflectionTestUtils.setField(user, "lastTimePasswordUpdated", LocalDate.of(2026, 1, 1));
        return user;
    }
}

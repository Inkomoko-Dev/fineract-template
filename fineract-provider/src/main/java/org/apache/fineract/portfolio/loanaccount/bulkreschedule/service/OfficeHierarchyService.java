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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;

/**
 * Service for managing office hierarchy operations in bulk rescheduling context. Provides methods
 * for retrieving office hierarchies and validating user access to offices.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeHierarchyService {

    private final OfficeRepository officeRepository;

    /**
     * Retrieves the given office and all of its child branch offices recursively.
     *
     * @param officeId the ID of the parent office
     * @return list of office IDs including the parent and all children
     * @throws GeneralPlatformDomainRuleException if office not found
     */
    public List<Long> getOfficeAndChildBranches(final Long officeId) {
        log.debug("Retrieving office {} and child branches", officeId);

        Optional<Office> officeOptional = officeRepository.findById(officeId);
        if (!officeOptional.isPresent()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.office.not.found",
                    "Office not found with ID: " + officeId);
        }

        Set<Long> officeIds = new HashSet<>();
        collectOfficeAndChildren(officeOptional.get(), officeIds);

        return new ArrayList<>(officeIds);
    }

    /**
     * Recursively collects the given office ID and all child office IDs.
     *
     * @param office the office to start from
     * @param officeIds set to accumulate office IDs
     */
    private void collectOfficeAndChildren(final Office office, final Set<Long> officeIds) {
        officeIds.add(office.getId());

        if (office.getChildren() != null && !office.getChildren().isEmpty()) {
            for (Office child : office.getChildren()) {
                collectOfficeAndChildren(child, officeIds);
            }
        }
    }

    /**
     * Validates that the user has access to the given office. Checks if the office is in the
     * user's list of accessible offices.
     *
     * @param user the application user
     * @param officeId the office ID to check access for
     * @return true if user has access, false otherwise
     */
    public boolean validateUserAccessToOffice(final AppUser user, final Long officeId) {
        if (user == null || officeId == null) {
            return false;
        }

        log.debug("Validating user {} access to office {}", user.getId(), officeId);

        // Get all offices accessible to the user
        List<Long> userAccessibleOffices = getUserAccessibleOffices(user);

        return userAccessibleOffices.contains(officeId);
    }

    /**
     * Retrieves all office IDs accessible to the given user. This includes their home office and
     * all descendant offices. Parent offices are deliberately excluded: access flows down an
     * office hierarchy, never upward.
     *
     * @param user the application user
     * @return list of office IDs the user can access
     */
    public List<Long> getUserAccessibleOffices(final AppUser user) {
        if (user == null) {
            return new ArrayList<>();
        }

        log.debug("Retrieving accessible offices for user {}", user.getId());

        // User has access to their home office
        Office homeOffice = user.getOffice();
        if (homeOffice == null) {
            return new ArrayList<>();
        }

        Set<Long> accessibleOffices = new HashSet<>();

        // Add home office and all its children
        collectOfficeAndChildren(homeOffice, accessibleOffices);

        return new ArrayList<>(accessibleOffices);
    }
}

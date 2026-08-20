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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.springframework.data.jpa.domain.Specification;

public final class BulkRescheduleExecutionSpecifications {

    private BulkRescheduleExecutionSpecifications() {
    }

    public static Specification<BulkRescheduleExecution> officeIdIn(
            final Collection<Long> officeIds) {

        return (root, query, criteriaBuilder) -> {

            if (officeIds == null || officeIds.isEmpty()) {
                /*
                 * No permitted offices should return no rows rather than
                 * accidentally returning all executions.
                 */
                return criteriaBuilder.disjunction();
            }

            return root.get("officeId").in(officeIds);
        };
    }

    public static Specification<BulkRescheduleExecution> hasStatus(
            final BulkRescheduleExecutionStatus status) {

        return (root, query, criteriaBuilder) -> {

            if (status == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.equal(
                    root.get("status"),
                    status);
        };
    }

    public static Specification<BulkRescheduleExecution> assignedTo(final Long approverId) {
        return (root, query, criteriaBuilder) -> approverId == null ? criteriaBuilder.conjunction()
                : criteriaBuilder.equal(root.get("approver").get("id"), approverId);
    }

    public static Specification<BulkRescheduleExecution> createdFrom(
            final LocalDateTime startDate) {

        return (root, query, criteriaBuilder) -> {

            if (startDate == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.greaterThanOrEqualTo(
                    root.get("createdAt"),
                    startDate);
        };
    }

    public static Specification<BulkRescheduleExecution> createdUntil(
            final LocalDateTime endDate) {

        return (root, query, criteriaBuilder) -> {

            if (endDate == null) {
                return criteriaBuilder.conjunction();
            }

            return criteriaBuilder.lessThanOrEqualTo(
                    root.get("createdAt"),
                    endDate);
        };
    }
}

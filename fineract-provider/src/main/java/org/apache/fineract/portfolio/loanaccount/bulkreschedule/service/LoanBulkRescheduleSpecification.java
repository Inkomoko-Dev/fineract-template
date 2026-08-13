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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleFilterDto;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specification builder for dynamic loan queries based on bulk reschedule filter criteria.
 * Provides factory methods for individual filter specifications that can be combined using AND
 * logic.
 */
public class LoanBulkRescheduleSpecification implements Specification<Loan> {

    private static final long serialVersionUID = 1L;
    private final List<Specification<Loan>> specifications = new ArrayList<>();

    /**
     * Specification to filter loans by office ID and include all child branches.
     *
     * @param officeIds IDs of the office and all child branches
     * @return Specification matching loans in the given offices
     */
    public static Specification<Loan> byOfficeIds(final List<Long> officeIds) {
        return (root, query, builder) -> {
            if (officeIds == null || officeIds.isEmpty()) {
                return builder.conjunction();
            }
            return root.get("client").get("office").get("id").in(officeIds);
        };
    }

    /**
     * Specification to filter loans by exact loan status. Matches the statusCode field.
     *
     * @param statusCode the loan status code to match (e.g., "300" for ACTIVE)
     * @return Specification matching loans with the given status
     */
    public static Specification<Loan> byLoanStatus(final Integer statusCode) {
        return (root, query, builder) -> {
            if (statusCode == null) {
                return builder.conjunction();
            }
            return builder.equal(root.get("loanStatus"), statusCode);
        };
    }

    /**
     * Specification to filter loans by loan product ID.
     *
     * @param productIds loan product IDs to match
     * @return Specification matching loans with the given product
     */
    public static Specification<Loan> byLoanProducts(final List<Long> productIds) {
        return (root, query, builder) -> {
            if (productIds == null || productIds.isEmpty()) {
                return builder.conjunction();
            }
            return root.get("loanProduct").get("id").in(productIds);
        };
    }

    /**
     * Specification to filter loans by loan officer ID.
     *
     * @param officerIds loan officer IDs to match
     * @return Specification matching loans assigned to the given officer
     */
    public static Specification<Loan> byLoanOfficers(final List<Long> officerIds) {
        return (root, query, builder) -> {
            if (officerIds == null || officerIds.isEmpty()) {
                return builder.conjunction();
            }
            return root.get("loanOfficer").get("id").in(officerIds);
        };
    }

    /**
     * Specification to filter loans by exact nominal interest rate per period.
     *
     * @param rate the nominal interest rate per period to match
     * @return Specification matching loans with the given nominal interest rate per period
     */
    public static Specification<Loan> byNominalInterestRatePerPeriod(final BigDecimal rate) {
        return (root, query, builder) -> {
            if (rate == null) {
                return builder.conjunction();
            }
            return builder.equal(root.get("loanRepaymentScheduleDetail").get("nominalInterestRatePerPeriod"), rate);
        };
    }

    /**
     * Creates a combined specification from the given filter DTO, applying all non-null filter
     * criteria with AND logic.
     *
     * @param filters the filter DTO containing criteria
     * @param userAccessibleOffices list of office IDs the user can access
     * @return combined Specification or empty conjunction if no filters apply
     * @throws GeneralPlatformDomainRuleException if user doesn't have access to selected office
     */
    public static Specification<Loan> createSpecification(final BulkRescheduleFilterDto filters,
            final List<Long> userAccessibleOffices) {

        if (filters == null) {
            return Specification.where(null);
        }

        // Office filter is mandatory.
        if (userAccessibleOffices == null || userAccessibleOffices.isEmpty()) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.office.required",
                    "At least one accessible office is required for bulk reschedule");
        }

        if (filters.getOfficeId() != null && !userAccessibleOffices.contains(filters.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException(
                    "error.msg.bulk.reschedule.office.access.denied",
                    "User does not have access to the selected office: " + filters.getOfficeId());
        }

        Specification<Loan> spec = Specification.where(null);

        // Mandatory filter by client offices.
        spec = spec.and(byOfficeIds(userAccessibleOffices));

        Integer statusCode = null;
        if (filters.getLoanStatus() != null && !filters.getLoanStatus().trim().isEmpty()) {
            try {
                statusCode = Integer.parseInt(filters.getLoanStatus());
            } catch (NumberFormatException e) {
                throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.invalid.status",
                        "Invalid loan status code: " + filters.getLoanStatus(), e);
            }
        }
        if (statusCode != null) {
            spec = spec.and(byLoanStatus(statusCode));
        }

        if (filters.getLoanProductIds() != null && !filters.getLoanProductIds().isEmpty()) {
            spec = spec.and(byLoanProducts(filters.getLoanProductIds()));
        }

        if (filters.getCurrentInterestRate() != null) {
            spec = spec.and(byNominalInterestRatePerPeriod(filters.getCurrentInterestRate()));
        }

        if (filters.getLoanOfficerIds() != null && !filters.getLoanOfficerIds().isEmpty()) {
            spec = spec.and(byLoanOfficers(filters.getLoanOfficerIds()));
        }

        return spec;
    }


    @Override
    public Predicate toPredicate(final Root<Loan> root, final CriteriaQuery<?> query, final CriteriaBuilder builder) {
        if (specifications.isEmpty()) {
            return builder.conjunction();
        }

        Predicate[] predicates = new Predicate[specifications.size()];
        for (int i = 0; i < specifications.size(); i++) {
            predicates[i] = specifications.get(i).toPredicate(root, query, builder);
        }

        return builder.and(predicates);
    }
}

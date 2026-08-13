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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepository;
import org.apache.fineract.organisation.staff.data.StaffData;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.FilterOptionsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.LoanOfficerOptionDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.LoanProductOptionDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.OfficeOptionDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.RescheduleDetailOptionsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.TemplateDataDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.UserPermissionsDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.ValidationRulesDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.RescheduleFromDateStrategy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanStatus;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.RescheduleLoansApiConstants;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.data.LoanRescheduleRequestData;
import org.apache.fineract.portfolio.loanaccount.rescheduleloan.service.LoanRescheduleRequestReadPlatformService;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductData;
import org.apache.fineract.portfolio.loanproduct.service.LoanProductReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;

/**
 * Service for providing template data for bulk reschedule operations. Supplies dropdown options,
 * validation rules, and user permissions needed by the UI.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRescheduleTemplateService {

    private final LoanProductReadPlatformService loanProductReadPlatformService;
    private final OfficeRepository officeRepository;
    private final StaffReadPlatformService staffReadPlatformService;
    private final PlatformSecurityContext platformSecurityContext;
    private final OfficeHierarchyService officeHierarchyService;
    private final LoanRescheduleRequestReadPlatformService loanRescheduleRequestReadPlatformService;

    /**
     * Retrieves template data for bulk reschedule operations including filter options, reschedule detail
     * options, validation rules, and user permissions.
     *
     * @param userOfficeId the office ID of the current user
     * @return TemplateDataDto containing all template data
     */
    public TemplateDataDto getTemplateData(final Long userOfficeId) {
        log.debug("Fetching bulk reschedule template data for office ID: {}", userOfficeId);

        FilterOptionsDto filterOptions = buildFilterOptions(userOfficeId);
        RescheduleDetailOptionsDto rescheduleDetailOptions = buildRescheduleDetailOptions();
        ValidationRulesDto validationRules = buildValidationRules();
        UserPermissionsDto userPermissions = buildUserPermissions();

        TemplateDataDto templateData = new TemplateDataDto();
        templateData.setFilterOptions(filterOptions);
        templateData.setRescheduleDetailOptions(rescheduleDetailOptions);
        templateData.setValidationRules(validationRules);
        templateData.setUserPermissions(userPermissions);

        log.debug("Bulk reschedule template data retrieved successfully");
        return templateData;
    }

    /**
     * Builds filter options by fetching offices, loan statuses, loan products, and loan officers.
     *
     * @param userOfficeId the office ID of the current user
     * @return FilterOptionsDto containing all filter options
     */
    private FilterOptionsDto buildFilterOptions(final Long userOfficeId) {
        log.debug("Building filter options for office ID: {}", userOfficeId);

        FilterOptionsDto filterOptions = new FilterOptionsDto();

        // Offices
        final AppUser currentUser = platformSecurityContext.authenticatedUser();
        final List<Long> accessibleOfficeIds = officeHierarchyService.getUserAccessibleOffices(currentUser);
        final List<Office> accessibleOffices = officeRepository.findAllById(accessibleOfficeIds);
        List<OfficeOptionDto> officeDtos = new ArrayList<>();
        for (Office office : accessibleOffices) {
            OfficeOptionDto officeDto = new OfficeOptionDto();
            officeDto.setId(office.getId());
            officeDto.setName(office.getName());
            officeDto.setHeadOffice(office.getParent() == null);
            officeDto.setParentOfficeId(office.getParent() != null && accessibleOfficeIds.contains(office.getParent().getId())
                    ? office.getParent().getId() : null);
            officeDto.setChildBranches(office.getChildren() == null ? List.of() : office.getChildren().stream()
                    .map(Office::getId).filter(accessibleOfficeIds::contains).toList());
            officeDtos.add(officeDto);
        }
        filterOptions.setOffices(officeDtos);

        // Loan statuses — only statuses that make sense as a reschedule filter
        List<EnumOptionData> loanStatuses = new ArrayList<>();
        loanStatuses.add(new EnumOptionData(LoanStatus.ACTIVE.getValue().longValue(),
                String.valueOf(LoanStatus.ACTIVE.getValue()), "Active"));
        loanStatuses.add(new EnumOptionData(LoanStatus.CLOSED_OBLIGATIONS_MET.getValue().longValue(),
                String.valueOf(LoanStatus.CLOSED_OBLIGATIONS_MET.getValue()), "Closed - Obligations Met"));
        loanStatuses.add(new EnumOptionData(LoanStatus.CLOSED_WRITTEN_OFF.getValue().longValue(),
                String.valueOf(LoanStatus.CLOSED_WRITTEN_OFF.getValue()), "Closed - Written Off"));
        loanStatuses.add(new EnumOptionData(LoanStatus.CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT.getValue().longValue(),
                String.valueOf(LoanStatus.CLOSED_RESCHEDULE_OUTSTANDING_AMOUNT.getValue()), "Closed - Rescheduled Outstanding"));
        loanStatuses.add(new EnumOptionData(LoanStatus.WITHDRAWN_BY_CLIENT.getValue().longValue(),
                String.valueOf(LoanStatus.WITHDRAWN_BY_CLIENT.getValue()), "Withdrawn"));
        loanStatuses.add(new EnumOptionData(LoanStatus.REJECTED.getValue().longValue(),
                String.valueOf(LoanStatus.REJECTED.getValue()), "Rejected"));
        filterOptions.setLoanStatuses(loanStatuses);

        // Loan products
        Collection<LoanProductData> loanProductDataCollection = loanProductReadPlatformService.retrieveAllLoanProducts();
        List<LoanProductOptionDto> loanProducts = new ArrayList<>();
        for (LoanProductData lpd : loanProductDataCollection) {
            LoanProductOptionDto productDto = new LoanProductOptionDto();
            productDto.setId(lpd.getId());
            productDto.setName(lpd.getName());
            loanProducts.add(productDto);
        }
        filterOptions.setLoanProducts(loanProducts);

        // Loan officers
        Collection<StaffData> staffDataCollection = staffReadPlatformService.retrieveAllStaffForDropdown(userOfficeId);
        List<LoanOfficerOptionDto> loanOfficers = new ArrayList<>();
        for (StaffData staffData : staffDataCollection) {
            LoanOfficerOptionDto loanOfficerDto = new LoanOfficerOptionDto();
            loanOfficerDto.setId(staffData.getId());
            loanOfficerDto.setName(staffData.getDisplayName() != null ? staffData.getDisplayName()
                    : staffData.getFirstname() + " " + staffData.getLastname());
            loanOfficerDto.setOfficeId(staffData.getOfficeId());
            loanOfficers.add(loanOfficerDto);
        }
        filterOptions.setLoanOfficers(loanOfficers);

        log.debug("Filter options built successfully");
        return filterOptions;
    }

    /**
     * Builds reschedule detail options by fetching reschedule reasons, overdue charge handling options,
     * and available carry-forward charges from existing code value configuration.
     * Also includes available reschedule-from-date strategies.
     *
     * @return RescheduleDetailOptionsDto containing all reschedule detail options
     */
    private RescheduleDetailOptionsDto buildRescheduleDetailOptions() {
        log.debug("Building reschedule detail options");

        LoanRescheduleRequestData data = loanRescheduleRequestReadPlatformService.retrieveAllRescheduleReasons(
                RescheduleLoansApiConstants.LOAN_RESCHEDULE_REASON,
                RescheduleLoansApiConstants.CHARGE_HANDLING_METHOD);

        RescheduleDetailOptionsDto options = new RescheduleDetailOptionsDto();
        options.setRescheduleReasons(data.getRescheduleReasons() != null ? new ArrayList<>(data.getRescheduleReasons()) : new ArrayList<>());
        options.setOverdueChargeHandlingOptions(data.getOverdueChargeHandlingOptions() != null
                ? new ArrayList<>(data.getOverdueChargeHandlingOptions()) : new ArrayList<>());
        options.setAvailableCarryForwardCharges(data.getAvailableCarryForwardCharges());
        options.setAdjustFuturePayments(data.getAdjustFuturePayments());

        List<EnumOptionData> strategies = new ArrayList<>();
        strategies.add(new EnumOptionData(1L, RescheduleFromDateStrategy.FIRST_INSTALLMENT.name(), "First Installment Date"));
        strategies.add(new EnumOptionData(2L, RescheduleFromDateStrategy.NEXT_UNPAID.name(), "Next Unpaid Installment Date"));
        options.setRescheduleFromDateStrategies(strategies);

        log.debug("Reschedule detail options built successfully");
        return options;
    }

    /**
     * Builds static validation rules for bulk reschedule operations.
     *
     * @return ValidationRulesDto containing validation rules
     */
    private ValidationRulesDto buildValidationRules() {
        log.debug("Building validation rules");

        ValidationRulesDto validationRules = new ValidationRulesDto();
        validationRules.setCurrentInterestRateIsExact(true);
        validationRules.setNewInterestRateIsManualInput(true);
        validationRules.setNewInterestRateMinValue(BigDecimal.ZERO);
        validationRules.setNewInterestRateMaxValue(new BigDecimal("100"));
        validationRules.setRequiresApproval(true);
        validationRules.setApprovalRoles(Arrays.asList("MANAGER", "HEAD_OFFICE_ADMIN"));

        log.debug("Validation rules built successfully");
        return validationRules;
    }

    /**
     * Builds user permissions based on the current user's roles and accessible offices.
     *
     * @return UserPermissionsDto containing user permissions
     */
    private UserPermissionsDto buildUserPermissions() {
        log.debug("Building user permissions");

        AppUser currentUser = platformSecurityContext.authenticatedUser();
        UserPermissionsDto userPermissions = new UserPermissionsDto();

        boolean canInitiate = hasPermission(currentUser, "CREATE_RESCHEDULELOAN");
        boolean canApprove = hasPermission(currentUser, "APPROVE_RESCHEDULELOAN");
        boolean canViewAudit = hasPermission(currentUser, "READ_RESCHEDULELOAN");

        userPermissions.setCanInitiateBulkReschedule(canInitiate);
        userPermissions.setCanApprove(canApprove);
        userPermissions.setCanViewAuditTrail(canViewAudit);

        List<Long> accessibleOffices = officeHierarchyService.getUserAccessibleOffices(currentUser);
        userPermissions.setAccessibleOffices(accessibleOffices);

        log.debug("User permissions built successfully");
        return userPermissions;
    }

    /**
     * Checks if the current user has a specific permission.
     *
     * @param user the application user
     * @param permission the permission name
     * @return true if user has permission, false otherwise
     */
    private boolean hasPermission(final AppUser user, final String permission) {
        if (user == null) {
            return false;
        }
        try {
            user.validateHasPermissionTo(permission);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

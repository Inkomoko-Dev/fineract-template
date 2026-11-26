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
package org.apache.fineract.portfolio.loanaccount.excessrefund;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRedrawAccountRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.excessrefund.api.ExcessRefundApiResource;
import org.apache.fineract.portfolio.loanaccount.excessrefund.service.ExcessRefundReadPlatformService;
import org.apache.fineract.portfolio.loanaccount.service.ClientBankDetailsResolver;
import org.apache.fineract.portfolio.paymenttype.service.PaymentTypeReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

class ExcessRefundReadPermissionTest {

    private final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final LoanRepositoryWrapper loanRepository = mock(LoanRepositoryWrapper.class);
    private final OfficeHierarchyService offices = mock(OfficeHierarchyService.class);
    private final ClientBankDetailsResolver bankDetails = mock(ClientBankDetailsResolver.class);
    private final ExcessRefundReadPlatformService readService = new ExcessRefundReadPlatformService(this.context, this.jdbcTemplate,
            this.loanRepository, mock(LoanRedrawAccountRepository.class), this.bankDetails,
            mock(PaymentTypeReadPlatformService.class), this.offices);

    @BeforeEach
    void setTenant() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        final Loan loan = mock(Loan.class);
        when(loan.getOfficeId()).thenReturn(1L);
        when(this.loanRepository.findOneWithNotFoundDetection(416456L)).thenReturn(loan);
        when(this.bankDetails.resolve(ArgumentMatchers.any(), ArgumentMatchers.eq(LoanDisbursementDetails.PaymentToType.CLIENT.getValue()),
                ArgumentMatchers.isNull(), ArgumentMatchers.isNull(), ArgumentMatchers.isNull()))
                .thenReturn(new ClientBankDetailsResolver.ResolvedClientPaymentDetails(null, null, null));
    }

    @AfterEach
    void clearTenant() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    @DisplayName("A user with read access to everything can list, open and export excess refunds")
    void allFunctionsReadUserSeesRefunds() {
        actAs(userWith(permission("ALL", "FUNCTIONS_READ")));
        final ExcessRefundApiResource resource = resource();

        assertDoesNotThrow(() -> resource.retrieveAll(null, null, null));
        assertDoesNotThrow(() -> resource.export(null));
        assertDoesNotThrow(() -> this.readService.retrieveTemplate(416456L));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> this.readService.retrieveOne(7L));
    }

    @Test
    @DisplayName("A user granted the excess refund read permission can list and export excess refunds")
    void refundReaderSeesRefunds() {
        actAs(userWith(permission("READ", "EXCESS_REFUND")));

        assertDoesNotThrow(() -> resource().retrieveAll(null, null, null));
        assertDoesNotThrow(() -> resource().export(null));
    }

    @Test
    @DisplayName("A user with no excess refund read access is still refused before any data is read")
    void userWithoutReadAccessIsRefused() {
        actAs(userWith(permission("READ", "LOAN")));
        final ExcessRefundApiResource resource = resource();

        assertThrows(NoAuthorizationException.class, () -> resource.retrieveAll(null, null, null));
        assertThrows(NoAuthorizationException.class, () -> resource.export(null));
        assertThrows(NoAuthorizationException.class, () -> this.readService.retrieveOne(7L));
        assertThrows(NoAuthorizationException.class, () -> this.readService.retrieveAll(null, null, null));
        assertThrows(NoAuthorizationException.class, () -> this.readService.retrieveTemplate(416456L));
        assertThrows(NoAuthorizationException.class, () -> this.readService.exportCsv(null));
        verify(this.jdbcTemplate, never()).query(ArgumentMatchers.anyString(), ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Object>>any(),
                ArgumentMatchers.<Object>any());
        verify(this.loanRepository, never()).findOneWithNotFoundDetection(416456L);
    }

    @SuppressWarnings("unchecked")
    private ExcessRefundApiResource resource() {
        return new ExcessRefundApiResource(this.context, this.readService, mock(PortfolioCommandSourceWritePlatformService.class),
                (DefaultToApiJsonSerializer<Object>) mock(DefaultToApiJsonSerializer.class));
    }

    private void actAs(final AppUser user) {
        when(this.context.authenticatedUser()).thenReturn(user);
        when(this.offices.validateUserAccessToOffice(user, 1L)).thenReturn(true);
    }

    private Permission permission(final String action, final String entity) {
        return new Permission("portfolio", entity, action);
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

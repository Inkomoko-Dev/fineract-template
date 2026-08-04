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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverTxnRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanproduct.domain.HistoricalCorrectionProductApprover;
import org.apache.fineract.portfolio.loanproduct.domain.HistoricalCorrectionProductApproverRepository;
import org.apache.fineract.useradministration.data.AppUserData;
import org.apache.fineract.useradministration.service.AppUserReadPlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Approver options are an intersection, not a union: holding the approve permission is not enough on its own, and being
 * mapped to the product is not enough on its own.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HistoricalPenaltyWaiverApproverOptionsTest {

    private static final Long LOAN_ID = 4001L;
    private static final Long PRODUCT_ID = 9L;
    private static final Long OFFICE_ID = 3L;

    private final AppUserReadPlatformService appUserReadPlatformService = mock(AppUserReadPlatformService.class);
    private final HistoricalCorrectionProductApproverRepository approverRepository = mock(
            HistoricalCorrectionProductApproverRepository.class);
    private final LoanRepositoryWrapper loanRepositoryWrapper = mock(LoanRepositoryWrapper.class);

    private HistoricalPenaltyWaiverReadPlatformService service() {
        final Loan loan = mock(Loan.class);
        when(loan.getOfficeId()).thenReturn(OFFICE_ID);
        when(loan.productId()).thenReturn(PRODUCT_ID);
        when(this.loanRepositoryWrapper.findOneWithNotFoundDetection(LOAN_ID)).thenReturn(loan);

        return new HistoricalPenaltyWaiverReadPlatformServiceImpl(mock(LoanHistoricalPenaltyWaiverRepository.class),
                mock(LoanHistoricalPenaltyWaiverTxnRepository.class), this.approverRepository, this.appUserReadPlatformService,
                this.loanRepositoryWrapper);
    }

    private void permittedUsers(final Long... userIds) {
        final List<AppUserData> users = Arrays.stream(userIds).map(id -> AppUserData.dropdown(id, "user" + id)).toList();
        when(this.appUserReadPlatformService.retrieveUsersByOfficeAndPermission(OFFICE_ID,
                HistoricalPenaltyWaiverReadPlatformServiceImpl.APPROVE_PERMISSION)).thenReturn(users);
    }

    private void productApprovers(final Long... userIds) {
        when(this.approverRepository.findByLoanProductId(PRODUCT_ID))
                .thenReturn(Arrays.stream(userIds).map(id -> HistoricalCorrectionProductApprover.create(PRODUCT_ID, id)).toList());
    }

    @Test
    public void onlyUsersWithBothThePermissionAndTheProductMappingAreOffered() {
        permittedUsers(10L, 11L, 12L);
        productApprovers(11L, 12L, 13L);

        final Collection<AppUserData> options = service().retrieveApproverOptions(LOAN_ID);

        assertEquals(2, options.size());
        assertTrue(options.stream().anyMatch(user -> user.hasIdentifyOf(11L)));
        assertTrue(options.stream().anyMatch(user -> user.hasIdentifyOf(12L)));
    }

    @Test
    public void thePermissionAloneDoesNotMakeSomeoneAnApprover() {
        permittedUsers(10L, 11L);
        productApprovers(99L);

        assertTrue(service().retrieveApproverOptions(LOAN_ID).isEmpty());
    }

    @Test
    public void theProductMappingAloneDoesNotMakeSomeoneAnApprover() {
        permittedUsers();
        productApprovers(10L, 11L);

        assertTrue(service().retrieveApproverOptions(LOAN_ID).isEmpty());
    }

    @Test
    public void aProductWithNoMappedApproversOffersNobody() {
        permittedUsers(10L, 11L);
        productApprovers();

        assertTrue(service().retrieveApproverOptions(LOAN_ID).isEmpty(),
                "an unmapped product must not silently fall back to every permission holder");
    }
}

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.data.SupplierDisbursementSnapshot;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSupplierDisbursementAudit;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSupplierDisbursementAuditRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupplierDisbursementAuditServiceTest {

    @Mock
    private LoanSupplierDisbursementAuditRepository auditRepository;

    @InjectMocks
    private SupplierDisbursementAuditService underTest;

    private Loan loan;
    private LoanDisbursementDetails detail;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        this.loan = org.mockito.Mockito.mock(Loan.class);
        this.detail = org.mockito.Mockito.mock(LoanDisbursementDetails.class);
    }

    @Test
    void recordsAuditWhenSupplierDisbursementFieldsChange() {
        when(this.loan.getId()).thenReturn(10L);
        when(this.detail.getId()).thenReturn(20L);
        final SupplierDisbursementSnapshot before = new SupplierDisbursementSnapshot(null, 1, "CLIENT", null, null, null, null, null);
        final SupplierDisbursementSnapshot after = new SupplierDisbursementSnapshot(42L, 2, "VENDOR", "Vendor A", "+251911000000", null,
                null, 1L);
        final AppUser user = org.mockito.Mockito.mock(AppUser.class);
        when(user.getId()).thenReturn(5L);
        when(user.getUsername()).thenReturn("kifiya-svc");

        this.underTest.recordChange(this.loan, this.detail, before, after,
                SupplierDisbursementAuditService.CHANGE_SOURCE_DISBURSEMENT_INSTRUCTION, user);

        final ArgumentCaptor<LoanSupplierDisbursementAudit> captor = ArgumentCaptor.forClass(LoanSupplierDisbursementAudit.class);
        verify(this.auditRepository).save(captor.capture());
        final LoanSupplierDisbursementAudit saved = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(10L, saved.getLoanId());
        org.junit.jupiter.api.Assertions.assertEquals(20L, saved.getLoanDisbursementDetailId());
        org.junit.jupiter.api.Assertions.assertEquals(42L, saved.getNewSupplierId());
        org.junit.jupiter.api.Assertions.assertEquals(2, saved.getNewPaymentTo());
        org.junit.jupiter.api.Assertions.assertEquals("Vendor A", saved.getNewBeneficiaryName());
        org.junit.jupiter.api.Assertions.assertEquals(SupplierDisbursementAuditService.CHANGE_SOURCE_DISBURSEMENT_INSTRUCTION,
                saved.getChangeSource());
    }

    @Test
    void skipsAuditWhenNoMeaningfulChange() {
        final SupplierDisbursementSnapshot snapshot = new SupplierDisbursementSnapshot(42L, 2, "VENDOR", "Vendor A", "+251911000000", null,
                null, 1L);

        this.underTest.recordChange(this.loan, this.detail, snapshot, snapshot,
                SupplierDisbursementAuditService.CHANGE_SOURCE_DISBURSEMENT_INSTRUCTION, null);

        verify(this.auditRepository, never()).save(any());
    }
}

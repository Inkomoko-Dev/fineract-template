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

import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.data.SupplierDisbursementSnapshot;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSupplierDisbursementAudit;
import org.apache.fineract.portfolio.loanaccount.domain.LoanSupplierDisbursementAuditRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SupplierDisbursementAuditService {

    public static final String CHANGE_SOURCE_DISBURSEMENT_INSTRUCTION = "DISBURSEMENT_INSTRUCTION";
    public static final String CHANGE_SOURCE_MANUAL_OVERRIDE = "MANUAL_OVERRIDE";

    private final LoanSupplierDisbursementAuditRepository auditRepository;

    public void recordChange(final Loan loan, final LoanDisbursementDetails disbursementDetail, final SupplierDisbursementSnapshot before,
            final SupplierDisbursementSnapshot after, final String changeSource, final AppUser changedBy) {
        if (loan == null || disbursementDetail == null || before == null || after == null || !hasMeaningfulChange(before, after)) {
            return;
        }
        final Long changedById = changedBy == null ? null : changedBy.getId();
        final String changedByUsername = changedBy == null ? changeSource : changedBy.getUsername();
        final OffsetDateTime changedOnDate = DateUtils.getOffsetDateTimeOfTenant();
        this.auditRepository.save(LoanSupplierDisbursementAudit.create(loan.getId(), disbursementDetail.getId(), changeSource,
                before.getSupplierId(), after.getSupplierId(), before.getPaymentTo(), after.getPaymentTo(), before.getBeneficiaryName(),
                after.getBeneficiaryName(), before.getClientPhoneNumber(), after.getClientPhoneNumber(), before.getClientAccountNumber(),
                after.getClientAccountNumber(), before.getClientBankName(), after.getClientBankName(), before.getPaymentTypeId(),
                after.getPaymentTypeId(), changedById, changedByUsername, changedOnDate));
    }

    private static boolean hasMeaningfulChange(final SupplierDisbursementSnapshot before, final SupplierDisbursementSnapshot after) {
        return !Objects.equals(before.getSupplierId(), after.getSupplierId()) || !Objects.equals(before.getPaymentTo(), after.getPaymentTo())
                || !Objects.equals(before.getBeneficiaryName(), after.getBeneficiaryName())
                || !Objects.equals(before.getClientPhoneNumber(), after.getClientPhoneNumber())
                || !Objects.equals(before.getClientAccountNumber(), after.getClientAccountNumber())
                || !Objects.equals(before.getClientBankName(), after.getClientBankName())
                || !Objects.equals(before.getPaymentTypeId(), after.getPaymentTypeId());
    }
}

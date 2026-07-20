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
package org.apache.fineract.portfolio.loanaccount.data;

import lombok.Getter;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;

@Getter
public class SupplierDisbursementSnapshot {

    private final Long supplierId;
    private final Integer paymentTo;
    private final String disbursementType;
    private final String beneficiaryName;
    private final String clientPhoneNumber;
    private final String clientAccountNumber;
    private final String clientBankName;
    private final Long paymentTypeId;

    public SupplierDisbursementSnapshot(final Long supplierId, final Integer paymentTo, final String disbursementType,
            final String beneficiaryName, final String clientPhoneNumber, final String clientAccountNumber, final String clientBankName,
            final Long paymentTypeId) {
        this.supplierId = supplierId;
        this.paymentTo = paymentTo;
        this.disbursementType = disbursementType;
        this.beneficiaryName = beneficiaryName;
        this.clientPhoneNumber = clientPhoneNumber;
        this.clientAccountNumber = clientAccountNumber;
        this.clientBankName = clientBankName;
        this.paymentTypeId = paymentTypeId;
    }

    public static SupplierDisbursementSnapshot from(final LoanDisbursementDetails detail) {
        if (detail == null) {
            return empty();
        }
        final PaymentType paymentType = detail.getPaymentType();
        final Long supplierId = detail.getSupplier() == null ? null : detail.getSupplier().getId();
        return new SupplierDisbursementSnapshot(supplierId, detail.getPaymentTo(), detail.getDisbursementType(),
                detail.getBeneficiaryName(), detail.getClientPhoneNumber(), detail.getClientAccountNumber(), detail.getClientBankName(),
                paymentType == null ? null : paymentType.getId());
    }

    public static SupplierDisbursementSnapshot empty() {
        return new SupplierDisbursementSnapshot(null, null, null, null, null, null, null, null);
    }
}

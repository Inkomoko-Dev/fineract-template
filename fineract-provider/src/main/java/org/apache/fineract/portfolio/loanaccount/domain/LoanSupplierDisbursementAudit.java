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
package org.apache.fineract.portfolio.loanaccount.domain;

import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Getter
@Entity
@Table(name = "m_loan_supplier_disbursement_audit")
public class LoanSupplierDisbursementAudit extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "loan_disbursement_detail_id")
    private Long loanDisbursementDetailId;

    @Column(name = "change_source", nullable = false, length = 50)
    private String changeSource;

    @Column(name = "previous_supplier_id")
    private Long previousSupplierId;

    @Column(name = "new_supplier_id")
    private Long newSupplierId;

    @Column(name = "previous_payment_to")
    private Integer previousPaymentTo;

    @Column(name = "new_payment_to")
    private Integer newPaymentTo;

    @Column(name = "previous_beneficiary_name", length = 150)
    private String previousBeneficiaryName;

    @Column(name = "new_beneficiary_name", length = 150)
    private String newBeneficiaryName;

    @Column(name = "previous_client_phone_number", length = 50)
    private String previousClientPhoneNumber;

    @Column(name = "new_client_phone_number", length = 50)
    private String newClientPhoneNumber;

    @Column(name = "previous_client_account_number", length = 150)
    private String previousClientAccountNumber;

    @Column(name = "new_client_account_number", length = 150)
    private String newClientAccountNumber;

    @Column(name = "previous_client_bank_name", length = 150)
    private String previousClientBankName;

    @Column(name = "new_client_bank_name", length = 150)
    private String newClientBankName;

    @Column(name = "previous_payment_type_id")
    private Long previousPaymentTypeId;

    @Column(name = "new_payment_type_id")
    private Long newPaymentTypeId;

    @Column(name = "changed_by_id")
    private Long changedById;

    @Column(name = "changed_by_username", length = 100)
    private String changedByUsername;

    @Column(name = "changed_on_date", nullable = false)
    private OffsetDateTime changedOnDate;

    protected LoanSupplierDisbursementAudit() {}

    private LoanSupplierDisbursementAudit(final Long loanId, final Long loanDisbursementDetailId, final String changeSource,
            final Long previousSupplierId, final Long newSupplierId, final Integer previousPaymentTo, final Integer newPaymentTo,
            final String previousBeneficiaryName, final String newBeneficiaryName, final String previousClientPhoneNumber,
            final String newClientPhoneNumber, final String previousClientAccountNumber, final String newClientAccountNumber,
            final String previousClientBankName, final String newClientBankName, final Long previousPaymentTypeId,
            final Long newPaymentTypeId, final Long changedById, final String changedByUsername, final OffsetDateTime changedOnDate) {
        this.loanId = loanId;
        this.loanDisbursementDetailId = loanDisbursementDetailId;
        this.changeSource = changeSource;
        this.previousSupplierId = previousSupplierId;
        this.newSupplierId = newSupplierId;
        this.previousPaymentTo = previousPaymentTo;
        this.newPaymentTo = newPaymentTo;
        this.previousBeneficiaryName = previousBeneficiaryName;
        this.newBeneficiaryName = newBeneficiaryName;
        this.previousClientPhoneNumber = previousClientPhoneNumber;
        this.newClientPhoneNumber = newClientPhoneNumber;
        this.previousClientAccountNumber = previousClientAccountNumber;
        this.newClientAccountNumber = newClientAccountNumber;
        this.previousClientBankName = previousClientBankName;
        this.newClientBankName = newClientBankName;
        this.previousPaymentTypeId = previousPaymentTypeId;
        this.newPaymentTypeId = newPaymentTypeId;
        this.changedById = changedById;
        this.changedByUsername = changedByUsername;
        this.changedOnDate = changedOnDate;
    }

    public static LoanSupplierDisbursementAudit create(final Long loanId, final Long loanDisbursementDetailId, final String changeSource,
            final Long previousSupplierId, final Long newSupplierId, final Integer previousPaymentTo, final Integer newPaymentTo,
            final String previousBeneficiaryName, final String newBeneficiaryName, final String previousClientPhoneNumber,
            final String newClientPhoneNumber, final String previousClientAccountNumber, final String newClientAccountNumber,
            final String previousClientBankName, final String newClientBankName, final Long previousPaymentTypeId,
            final Long newPaymentTypeId, final Long changedById, final String changedByUsername, final OffsetDateTime changedOnDate) {
        return new LoanSupplierDisbursementAudit(loanId, loanDisbursementDetailId, changeSource, previousSupplierId, newSupplierId,
                previousPaymentTo, newPaymentTo, previousBeneficiaryName, newBeneficiaryName, previousClientPhoneNumber,
                newClientPhoneNumber, previousClientAccountNumber, newClientAccountNumber, previousClientBankName, newClientBankName,
                previousPaymentTypeId, newPaymentTypeId, changedById, changedByUsername, changedOnDate);
    }
}

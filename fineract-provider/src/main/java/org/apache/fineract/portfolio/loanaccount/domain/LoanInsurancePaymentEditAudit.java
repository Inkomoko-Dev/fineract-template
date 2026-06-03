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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_loan_insurance_payment_edit_audit")
public class LoanInsurancePaymentEditAudit extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "loan_charge_id", nullable = false)
    private Long loanChargeId;

    @Column(name = "charge_id", nullable = false)
    private Long chargeId;

    @Column(name = "original_transaction_id", nullable = false)
    private Long originalTransactionId;

    @Column(name = "adjustment_transaction_id")
    private Long adjustmentTransactionId;

    @Column(name = "previous_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal previousAmount;

    @Column(name = "new_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal newAmount;

    @Column(name = "amount_delta", scale = 6, precision = 19, nullable = false)
    private BigDecimal amountDelta;

    @Column(name = "previous_payment_type_id")
    private Long previousPaymentTypeId;

    @Column(name = "previous_payment_type_name", length = 100)
    private String previousPaymentTypeName;

    @Column(name = "new_payment_type_id")
    private Long newPaymentTypeId;

    @Column(name = "new_payment_type_name", length = 100)
    private String newPaymentTypeName;

    @Column(name = "previous_payment_detail_id")
    private Long previousPaymentDetailId;

    @Column(name = "new_payment_detail_id")
    private Long newPaymentDetailId;

    @Column(name = "previous_fund_source_gl_account_id")
    private Long previousFundSourceGlAccountId;

    @Column(name = "new_fund_source_gl_account_id")
    private Long newFundSourceGlAccountId;

    @Column(name = "previous_income_gl_account_id")
    private Long previousIncomeGlAccountId;

    @Column(name = "new_income_gl_account_id")
    private Long newIncomeGlAccountId;

    @Column(name = "reason", length = 1000, nullable = false)
    private String reason;

    @Column(name = "adjustedby_id", nullable = false)
    private Long adjustedByUserId;

    @Column(name = "adjustedby_username", length = 100, nullable = false)
    private String adjustedByUsername;

    @Column(name = "adjustedby_roles", length = 1000)
    private String adjustedByRoles;

    @Column(name = "adjusted_on_date", nullable = false)
    private OffsetDateTime adjustedOnDate;

    @Column(name = "charge_paid_by_backfilled", nullable = false)
    private boolean chargePaidByBackfilled;

    protected LoanInsurancePaymentEditAudit() {}

    private LoanInsurancePaymentEditAudit(final Long loanId, final Long clientId, final Long productId, final Long officeId,
            final Long loanChargeId, final Long chargeId, final Long originalTransactionId, final Long adjustmentTransactionId,
            final BigDecimal previousAmount, final BigDecimal newAmount, final BigDecimal amountDelta,
            final Long previousPaymentTypeId, final String previousPaymentTypeName, final Long newPaymentTypeId,
            final String newPaymentTypeName, final Long previousPaymentDetailId, final Long newPaymentDetailId,
            final Long previousFundSourceGlAccountId, final Long newFundSourceGlAccountId,
            final Long previousIncomeGlAccountId, final Long newIncomeGlAccountId, final String reason,
            final Long adjustedByUserId, final String adjustedByUsername, final String adjustedByRoles,
            final OffsetDateTime adjustedOnDate, final boolean chargePaidByBackfilled) {
        this.loanId = loanId;
        this.clientId = clientId;
        this.productId = productId;
        this.officeId = officeId;
        this.loanChargeId = loanChargeId;
        this.chargeId = chargeId;
        this.originalTransactionId = originalTransactionId;
        this.adjustmentTransactionId = adjustmentTransactionId;
        this.previousAmount = previousAmount;
        this.newAmount = newAmount;
        this.amountDelta = amountDelta;
        this.previousPaymentTypeId = previousPaymentTypeId;
        this.previousPaymentTypeName = previousPaymentTypeName;
        this.newPaymentTypeId = newPaymentTypeId;
        this.newPaymentTypeName = newPaymentTypeName;
        this.previousPaymentDetailId = previousPaymentDetailId;
        this.newPaymentDetailId = newPaymentDetailId;
        this.previousFundSourceGlAccountId = previousFundSourceGlAccountId;
        this.newFundSourceGlAccountId = newFundSourceGlAccountId;
        this.previousIncomeGlAccountId = previousIncomeGlAccountId;
        this.newIncomeGlAccountId = newIncomeGlAccountId;
        this.reason = reason;
        this.adjustedByUserId = adjustedByUserId;
        this.adjustedByUsername = adjustedByUsername;
        this.adjustedByRoles = adjustedByRoles;
        this.adjustedOnDate = adjustedOnDate;
        this.chargePaidByBackfilled = chargePaidByBackfilled;
    }

    public static LoanInsurancePaymentEditAudit create(final Long loanId, final Long clientId, final Long productId,
            final Long officeId, final Long loanChargeId, final Long chargeId, final Long originalTransactionId,
            final Long adjustmentTransactionId, final BigDecimal previousAmount, final BigDecimal newAmount,
            final BigDecimal amountDelta, final Long previousPaymentTypeId, final String previousPaymentTypeName,
            final Long newPaymentTypeId, final String newPaymentTypeName, final Long previousPaymentDetailId,
            final Long newPaymentDetailId, final Long previousFundSourceGlAccountId, final Long newFundSourceGlAccountId,
            final Long previousIncomeGlAccountId, final Long newIncomeGlAccountId, final String reason,
            final Long adjustedByUserId, final String adjustedByUsername, final String adjustedByRoles,
            final OffsetDateTime adjustedOnDate, final boolean chargePaidByBackfilled) {
        return new LoanInsurancePaymentEditAudit(loanId, clientId, productId, officeId, loanChargeId, chargeId,
                originalTransactionId, adjustmentTransactionId, previousAmount, newAmount, amountDelta, previousPaymentTypeId,
                previousPaymentTypeName, newPaymentTypeId, newPaymentTypeName, previousPaymentDetailId, newPaymentDetailId,
                previousFundSourceGlAccountId, newFundSourceGlAccountId, previousIncomeGlAccountId, newIncomeGlAccountId,
                reason, adjustedByUserId, adjustedByUsername, adjustedByRoles, adjustedOnDate, chargePaidByBackfilled);
    }

    public Long getNewFundSourceGlAccountId() {
        return this.newFundSourceGlAccountId;
    }

    public Long getNewIncomeGlAccountId() {
        return this.newIncomeGlAccountId;
    }
}

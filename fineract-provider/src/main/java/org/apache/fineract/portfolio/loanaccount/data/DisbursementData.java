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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable data object representing disbursement information.
 */
public class DisbursementData implements Comparable<DisbursementData> {

    @SuppressWarnings("unused")
    private final Long id;
    private final LocalDate expectedDisbursementDate;
    private final LocalDate actualDisbursementDate;
    private final BigDecimal principal;
    private final BigDecimal netDisbursalAmount;
    @SuppressWarnings("unused")
    private final String loanChargeId;
    private final BigDecimal chargeAmount;
    private final BigDecimal waivedChargeAmount;
    private final Integer paymentTo;
    private final String disbursementType;
    private final String beneficiaryName;
    private final String clientPhoneNumber;
    private final String clientAccountNumber;
    private final String clientBankName;
    private final Long paymentTypeId;
    private final String paymentTypeName;
    private final Long supplierId;
    private final String supplierExternalId;
    private final String supplierName;
    private final String supplierSourceSystem;

    // import fields
    private transient Integer rowIndex;
    private String dateFormat;
    private String locale;
    private String note;
    private transient String linkAccountId;

    public static DisbursementData importInstance(LocalDate actualDisbursementDate, String linkAccountId, Integer rowIndex, String locale,
            String dateFormat) {
        return new DisbursementData(actualDisbursementDate, linkAccountId, rowIndex, locale, dateFormat);
    }

    private DisbursementData(LocalDate actualDisbursementDate, String linkAccountId, Integer rowIndex, String locale, String dateFormat) {
        this.dateFormat = dateFormat;
        this.locale = locale;
        this.actualDisbursementDate = actualDisbursementDate;
        this.rowIndex = rowIndex;
        this.note = "";
        this.linkAccountId = linkAccountId;
        this.id = null;
        this.expectedDisbursementDate = null;
        this.principal = null;
        this.loanChargeId = null;
        this.chargeAmount = null;
        this.waivedChargeAmount = null;
        this.netDisbursalAmount = null;
        this.paymentTo = null;
        this.disbursementType = null;
        this.beneficiaryName = null;
        this.clientPhoneNumber = null;
        this.clientAccountNumber = null;
        this.clientBankName = null;
        this.paymentTypeId = null;
        this.paymentTypeName = null;
        this.supplierId = null;
        this.supplierExternalId = null;
        this.supplierName = null;
        this.supplierSourceSystem = null;
    }

    public String getLinkAccountId() {
        return linkAccountId;
    }

    public DisbursementData(Long id, final LocalDate expectedDisbursementDate, final LocalDate actualDisbursementDate,
            final BigDecimal principalDisbursed, final BigDecimal netDisbursalAmount, final String loanChargeId, BigDecimal chargeAmount,
            BigDecimal waivedChargeAmount) {
        this(id, expectedDisbursementDate, actualDisbursementDate, principalDisbursed, netDisbursalAmount, loanChargeId, chargeAmount,
                waivedChargeAmount, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public DisbursementData(final Long id, final LocalDate expectedDisbursementDate, final LocalDate actualDisbursementDate,
            final BigDecimal principalDisbursed, final BigDecimal netDisbursalAmount, final String loanChargeId,
            final BigDecimal chargeAmount, final BigDecimal waivedChargeAmount, final Integer paymentTo, final String disbursementType,
            final String beneficiaryName, final String clientPhoneNumber, final String clientAccountNumber, final String clientBankName,
            final Long paymentTypeId, final String paymentTypeName, final Long supplierId, final String supplierExternalId,
            final String supplierName, final String supplierSourceSystem) {
        this.id = id;
        this.expectedDisbursementDate = expectedDisbursementDate;
        this.actualDisbursementDate = actualDisbursementDate;
        this.principal = principalDisbursed;
        this.loanChargeId = loanChargeId;
        this.chargeAmount = chargeAmount;
        this.waivedChargeAmount = waivedChargeAmount;
        this.netDisbursalAmount = netDisbursalAmount;
        this.paymentTo = paymentTo;
        this.disbursementType = disbursementType;
        this.beneficiaryName = beneficiaryName;
        this.clientPhoneNumber = clientPhoneNumber;
        this.clientAccountNumber = clientAccountNumber;
        this.clientBankName = clientBankName;
        this.paymentTypeId = paymentTypeId;
        this.paymentTypeName = paymentTypeName;
        this.supplierId = supplierId;
        this.supplierExternalId = supplierExternalId;
        this.supplierName = supplierName;
        this.supplierSourceSystem = supplierSourceSystem;
    }

    public LocalDate disbursementDate() {
        LocalDate disbursementDate = this.expectedDisbursementDate;
        if (this.actualDisbursementDate != null) {
            disbursementDate = this.actualDisbursementDate;
        }
        return disbursementDate;
    }

    public BigDecimal amount() {
        return this.principal;
    }

    public BigDecimal getChargeAmount() {
        return this.chargeAmount;
    }

    public Integer getPaymentTo() {
        return this.paymentTo;
    }

    public String getDisbursementType() {
        return this.disbursementType;
    }

    public String getBeneficiaryName() {
        return this.beneficiaryName;
    }

    public String getClientPhoneNumber() {
        return this.clientPhoneNumber;
    }

    public String getClientAccountNumber() {
        return this.clientAccountNumber;
    }

    public String getClientBankName() {
        return this.clientBankName;
    }

    public Long getPaymentTypeId() {
        return this.paymentTypeId;
    }

    public String getPaymentTypeName() {
        return this.paymentTypeName;
    }

    public Long getSupplierId() {
        return this.supplierId;
    }

    public String getSupplierExternalId() {
        return this.supplierExternalId;
    }

    public String getSupplierName() {
        return this.supplierName;
    }

    public String getSupplierSourceSystem() {
        return this.supplierSourceSystem;
    }

    public boolean isDisbursed() {
        return this.actualDisbursementDate != null;
    }

    @Override
    public int compareTo(final DisbursementData obj) {
        if (obj == null) {
            return -1;
        }

        return obj.expectedDisbursementDate.compareTo(this.expectedDisbursementDate);
    }

    public boolean isDueForDisbursement(final LocalDate fromNotInclusive, final LocalDate upToAndInclusive) {
        final LocalDate dueDate = disbursementDate();
        return occursOnDayFromAndUpToAndIncluding(fromNotInclusive, upToAndInclusive, dueDate);
    }

    private boolean occursOnDayFromAndUpToAndIncluding(final LocalDate fromNotInclusive, final LocalDate upToAndInclusive,
            final LocalDate target) {
        return target != null && target.isAfter(fromNotInclusive) && !target.isAfter(upToAndInclusive);
    }

    public BigDecimal getWaivedChargeAmount() {
        if (this.waivedChargeAmount == null) {
            return BigDecimal.ZERO;
        }
        return this.waivedChargeAmount;
    }

}

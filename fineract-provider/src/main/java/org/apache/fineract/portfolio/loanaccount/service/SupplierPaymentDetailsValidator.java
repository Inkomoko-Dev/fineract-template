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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.SupplierPaymentDetails;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.supplier.data.SupplierApiConstants;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierStatus;
import org.apache.fineract.portfolio.supplier.domain.SupplierSyncStatus;
import org.springframework.stereotype.Component;

@Component
public class SupplierPaymentDetailsValidator {

    public SupplierPaymentDetails validateAndExtract(final Supplier supplier) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();

        if (supplier == null) {
            dataValidationErrors.add(ApiParameterError.parameterError("error.msg.supplier.not.found",
                    "Supplier record was not found for the provided identifiers.", DisbursementInstructionApiConstants.SUPPLIER_EXTERNAL_ID,
                    null));
            throwValidationErrors(dataValidationErrors);
        }

        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.supplier.status.inactive",
                    "Supplier must be ACTIVE to create a disbursement instruction.", SupplierApiConstants.STATUS,
                    supplier.getStatus() == null ? null : supplier.getStatus().name()));
        }

        if (supplier.getSyncStatus() != SupplierSyncStatus.SUCCESS) {
            dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.supplier.syncStatus.invalid",
                    "Supplier sync status must be SUCCESS.", SupplierApiConstants.SYNC_STATUS,
                    supplier.getSyncStatus() == null ? null : supplier.getSyncStatus().name()));
        }

        final PaymentType paymentType = supplier.getPaymentType();
        if (paymentType == null) {
            dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.supplier.paymentTypeId.required",
                    "Supplier payment type is required.", SupplierApiConstants.PAYMENT_TYPE_ID, null));
        } else {
            dataValidationErrors.addAll(validatePaymentFields(paymentType, supplier.getPaymentPhoneNumber(),
                    supplier.getPaymentAccountNumber(), supplier.getPaymentBankName(), supplier.getPaymentAccountName()));
        }

        final String beneficiaryName = supplier.resolveBeneficiaryName();
        if (StringUtils.isBlank(beneficiaryName)) {
            dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.supplier.beneficiaryName.required",
                    "Supplier account name or display name is required as beneficiary.", SupplierApiConstants.PAYMENT_ACCOUNT_NAME,
                    beneficiaryName));
        }

        throwValidationErrors(dataValidationErrors);

        return new SupplierPaymentDetails(paymentType, supplier.getPaymentPhoneNumber(), supplier.getPaymentAccountNumber(),
                supplier.getPaymentBankName(), supplier.getPaymentAccountName(), beneficiaryName);
    }

    /**
     * Shared payment-detail rules for supplier registration and disbursement instruction.
     * Phone is always required. Bank types also require account number, bank name, and account name.
     */
    public List<ApiParameterError> validatePaymentFields(final PaymentType paymentType, final String paymentPhoneNumber,
            final String paymentAccountNumber, final String paymentBankName, final String paymentAccountName) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        if (paymentType == null) {
            dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.supplier.paymentTypeId.required",
                    "Supplier payment type is required.", SupplierApiConstants.PAYMENT_TYPE_ID, null));
            return dataValidationErrors;
        }

        if (StringUtils.isBlank(paymentPhoneNumber)) {
            dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.supplier.paymentPhoneNumber.required",
                    "Supplier phone number is required for all payment types.", SupplierApiConstants.PAYMENT_PHONE_NUMBER,
                    paymentPhoneNumber));
        }

        final boolean isCash = Boolean.TRUE.equals(paymentType.isCashPayment());
        final boolean isMobileMoney = Boolean.TRUE.equals(paymentType.isMobileMoney());
        if (!isCash && !isMobileMoney) {
            if (StringUtils.isBlank(paymentAccountNumber)) {
                dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.supplier.paymentAccountNumber.required",
                        "Supplier bank account number is required for this payment type.", SupplierApiConstants.PAYMENT_ACCOUNT_NUMBER,
                        paymentAccountNumber));
            }
            if (StringUtils.isBlank(paymentBankName)) {
                dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.supplier.paymentBankName.required",
                        "Supplier bank name is required for this payment type.", SupplierApiConstants.PAYMENT_BANK_NAME, paymentBankName));
            }
            if (StringUtils.isBlank(paymentAccountName)) {
                dataValidationErrors.add(ApiParameterError.parameterError("validation.msg.supplier.paymentAccountName.required",
                        "Supplier bank account name is required for this payment type.", SupplierApiConstants.PAYMENT_ACCOUNT_NAME,
                        paymentAccountName));
            }
        }
        return dataValidationErrors;
    }

    public void validatePaymentFieldsOrThrow(final PaymentType paymentType, final String paymentPhoneNumber,
            final String paymentAccountNumber, final String paymentBankName, final String paymentAccountName) {
        throwValidationErrors(
                validatePaymentFields(paymentType, paymentPhoneNumber, paymentAccountNumber, paymentBankName, paymentAccountName));
    }

    private void throwValidationErrors(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.disbursementInstruction.supplier.invalid",
                    "Supplier information is missing or invalid.", dataValidationErrors);
        }
    }
}

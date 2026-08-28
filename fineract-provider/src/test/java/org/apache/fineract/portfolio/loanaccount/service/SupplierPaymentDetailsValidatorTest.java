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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.loanaccount.data.SupplierPaymentDetails;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplierPaymentDetailsValidatorTest {

    private final SupplierPaymentDetailsValidator validator = new SupplierPaymentDetailsValidator();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void extractsMobileMoneyPaymentDetails() {
        final PaymentType paymentType = PaymentType.create("MoMo", "Mobile money", false, true, 1L);
        final Supplier supplier = Supplier.create("KIFIYA", "SUP-001", "Abebe Trading", "Abebe", null, null, null, null, null, null,
                SupplierStatus.ACTIVE);
        supplier.updatePaymentDetails(paymentType, "+251911000000", null, null, null);

        final SupplierPaymentDetails details = this.validator.validateAndExtract(supplier);

        assertEquals(paymentType, details.getPaymentType());
        assertEquals("+251911000000", details.getPhoneNumber());
        assertEquals("Abebe", details.getBeneficiaryName());
    }

    @Test
    void extractsBankPaymentDetailsWithAccountName() {
        final PaymentType paymentType = PaymentType.create("Bank", "Bank transfer", false, false, 2L);
        final Supplier supplier = Supplier.create("KIFIYA", "SUP-002", "Bekele Trading", "Bekele", null, null, null, null, null, null,
                SupplierStatus.ACTIVE);
        supplier.updatePaymentDetails(paymentType, "+251911000001", "1000123456789", "Commercial Bank of Ethiopia", "Bekele Account Name");

        final SupplierPaymentDetails details = this.validator.validateAndExtract(supplier);

        assertEquals("1000123456789", details.getAccountNumber());
        assertEquals("Commercial Bank of Ethiopia", details.getBankName());
        assertEquals("Bekele Account Name", details.getAccountName());
        assertEquals("Bekele Account Name", details.getBeneficiaryName());
    }

    @Test
    void rejectsBankPaymentWithoutAccountName() {
        final PaymentType paymentType = PaymentType.create("Bank", "Bank transfer", false, false, 2L);
        final Supplier supplier = Supplier.create("KIFIYA", "SUP-002", "Bekele Trading", "Bekele", null, null, null, null, null, null,
                SupplierStatus.ACTIVE);
        supplier.updatePaymentDetails(paymentType, "+251911000001", "1000123456789", "Commercial Bank of Ethiopia", null);

        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateAndExtract(supplier));
    }

    @Test
    void rejectsMissingPhoneForAnyPaymentType() {
        final PaymentType paymentType = PaymentType.create("Cash", "Cash", true, false, 3L);
        final Supplier supplier = Supplier.create("KIFIYA", "SUP-003", "Cash Supplier", "Cash Supplier", null, null, null, null, null, null,
                SupplierStatus.ACTIVE);
        supplier.updatePaymentDetails(paymentType, null, null, null, null);

        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateAndExtract(supplier));
    }

    @Test
    void rejectsInactiveSupplier() {
        final PaymentType paymentType = PaymentType.create("MoMo", "Mobile money", false, true, 1L);
        final Supplier supplier = Supplier.create("KIFIYA", "SUP-001", "Abebe Trading", null, null, null, null, null, null, null,
                SupplierStatus.INACTIVE);
        supplier.updatePaymentDetails(paymentType, "+251911000000", null, null, null);

        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateAndExtract(supplier));
    }

    @Test
    void rejectsSupplierWithoutPaymentType() {
        final Supplier supplier = Supplier.create("KIFIYA", "SUP-001", "Abebe Trading", null, null, null, null, null, null, null,
                SupplierStatus.ACTIVE);

        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateAndExtract(supplier));
    }

    @Test
    void rejectsFailedSyncSupplier() {
        final PaymentType paymentType = PaymentType.create("MoMo", "Mobile money", false, true, 1L);
        final Supplier supplier = Supplier.create("KIFIYA", "SUP-001", "Abebe Trading", null, null, null, null, null, null, null,
                SupplierStatus.ACTIVE);
        supplier.updatePaymentDetails(paymentType, "+251911000000", null, null, null);
        supplier.markFailed("sync error");

        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateAndExtract(supplier));
    }
}

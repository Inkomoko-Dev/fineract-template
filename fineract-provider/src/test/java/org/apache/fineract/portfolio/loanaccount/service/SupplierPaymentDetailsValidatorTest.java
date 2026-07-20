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

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.data.SupplierPaymentDetails;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierStatus;
import org.junit.jupiter.api.Test;

class SupplierPaymentDetailsValidatorTest {

    private final SupplierPaymentDetailsValidator validator = new SupplierPaymentDetailsValidator();

    @Test
    void extractsMobileMoneyPaymentDetails() {
        final PaymentType paymentType = PaymentType.create("MoMo", "Mobile money", false, true, 1L);
        final Supplier supplier = Supplier.create("KIFIYA", "SUP-001", "Abebe Trading", "Abebe", null, null, null, null, null, null,
                SupplierStatus.ACTIVE);
        supplier.updatePaymentDetails(paymentType, "+251911000000", null, null);

        final SupplierPaymentDetails details = this.validator.validateAndExtract(supplier);

        assertEquals(paymentType, details.getPaymentType());
        assertEquals("+251911000000", details.getPhoneNumber());
        assertEquals("Abebe", details.getBeneficiaryName());
    }

    @Test
    void rejectsInactiveSupplier() {
        final PaymentType paymentType = PaymentType.create("MoMo", "Mobile money", false, true, 1L);
        final Supplier supplier = Supplier.create("KIFIYA", "SUP-001", "Abebe Trading", null, null, null, null, null, null, null,
                SupplierStatus.INACTIVE);
        supplier.updatePaymentDetails(paymentType, "+251911000000", null, null);

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
        supplier.updatePaymentDetails(paymentType, "+251911000000", null, null);
        supplier.markFailed("sync error");

        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateAndExtract(supplier));
    }
}

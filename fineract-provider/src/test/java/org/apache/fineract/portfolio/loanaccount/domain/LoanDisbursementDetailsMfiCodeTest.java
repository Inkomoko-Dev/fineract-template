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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.junit.jupiter.api.Test;

public class LoanDisbursementDetailsMfiCodeTest {

    private LoanDisbursementDetails disbursementDetail() {
        return new LoanDisbursementDetails(LocalDate.of(2026, 2, 1), null, BigDecimal.valueOf(100000), BigDecimal.valueOf(100000));
    }

    @Test
    public void aSuppliedCodeIsStored() {
        final LoanDisbursementDetails detail = disbursementDetail();

        detail.applyMfiCodeIfProvided("MFI-001");

        assertEquals("MFI-001", detail.getMfiCode());
    }

    @Test
    public void aSuppliedCodeIsTrimmed() {
        final LoanDisbursementDetails detail = disbursementDetail();

        detail.applyMfiCodeIfProvided("  MFI-001  ");

        assertEquals("MFI-001", detail.getMfiCode());
    }

    @Test
    public void aStoredCodeSurvivesACommandThatOmitsTheParameter() {
        final LoanDisbursementDetails detail = disbursementDetail();
        detail.applyMfiCodeIfProvided("MFI-001");

        detail.applyMfiCodeIfProvided("");
        detail.applyMfiCodeIfProvided("   ");
        detail.applyMfiCodeIfProvided(null);

        assertEquals("MFI-001", detail.getMfiCode());
    }

    @Test
    public void aStoredCodeIsReplacedByALaterEdit() {
        final LoanDisbursementDetails detail = disbursementDetail();
        detail.applyMfiCodeIfProvided("MFI-001");

        detail.applyMfiCodeIfProvided("MFI-002");

        assertEquals("MFI-002", detail.getMfiCode());
    }

    @Test
    public void anUncapturedCodeStaysNull() {
        final LoanDisbursementDetails detail = disbursementDetail();

        detail.applyMfiCodeIfProvided(null);

        assertNull(detail.getMfiCode());
    }

    @Test
    public void firstNonBlankMfiCodeReturnsTrimmedValueFromFirstDetail() {
        final LoanDisbursementDetails blank = disbursementDetail();
        blank.applyMfiCodeIfProvided("   ");
        final LoanDisbursementDetails withCode = disbursementDetail();
        withCode.applyMfiCodeIfProvided("  ET-MFI-02  ");
        assertEquals("ET-MFI-02", LoanDisbursementDetails.firstNonBlankMfiCode(List.of(blank, withCode)));
    }

    @Test
    public void firstNonBlankMfiCodeReturnsNullWhenNonePresent() {
        final LoanDisbursementDetails blank = disbursementDetail();
        blank.applyMfiCodeIfProvided("  ");
        assertNull(LoanDisbursementDetails.firstNonBlankMfiCode(List.of(blank)));
        assertNull(LoanDisbursementDetails.firstNonBlankMfiCode(null));
        assertNull(LoanDisbursementDetails.firstNonBlankMfiCode(List.of()));
    }

    @Test
    public void paymentDetailInheritsMfiWhenCommandLeftBlank() {
        final PaymentType paymentType = PaymentType.create("Cash", "Cash", true, false, 1L);
        final PaymentDetail paymentDetail = PaymentDetail.instance(paymentType, null, null, null, null, null, null);
        final LoanDisbursementDetails detail = disbursementDetail();
        detail.applyMfiCodeIfProvided("ET-MFI-02");
        final String inherited = LoanDisbursementDetails.firstNonBlankMfiCode(List.of(detail));
        if (paymentDetail.getMfiCode() == null || paymentDetail.getMfiCode().isBlank()) {
            paymentDetail.setMfiCode(inherited);
        }
        assertEquals("ET-MFI-02", paymentDetail.getMfiCode());
    }
}

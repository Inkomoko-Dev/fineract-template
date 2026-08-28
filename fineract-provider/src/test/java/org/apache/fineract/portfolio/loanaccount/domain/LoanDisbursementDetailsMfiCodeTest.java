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
}

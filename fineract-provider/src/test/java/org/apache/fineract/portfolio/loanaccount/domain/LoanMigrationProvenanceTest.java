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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.apache.fineract.organisation.office.domain.Office;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class LoanMigrationProvenanceTest {

    @Test
    public void aLoanIsNotMigratedByDefault() {
        final Loan loan = new Loan();

        assertThat(loan.isMigrated()).isFalse();
        assertThat(loan.getMigratedOnDate()).isNull();
        assertThat(loan.getMigratedFromOffice()).isNull();
    }

    @Test
    public void aMigratedLoanCarriesItsOwnProvenanceIndependentOfTheClient() {
        // The Kenya Capital cohort moved the portfolio without moving the client, so the loan records
        // where it came from on its own rather than inheriting the client's office.
        final Office sourceOffice = Office.headOffice("Inkomoko - Kenya", LocalDate.of(2009, 11, 22), null);

        final Loan loan = new Loan();
        ReflectionTestUtils.setField(loan, "migrated", true);
        ReflectionTestUtils.setField(loan, "migratedOnDate", LocalDate.of(2026, 8, 1));
        ReflectionTestUtils.setField(loan, "migratedFromOffice", sourceOffice);

        assertThat(loan.isMigrated()).isTrue();
        assertThat(loan.getMigratedOnDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(loan.getMigratedFromOffice().getName()).isEqualTo("Inkomoko - Kenya");
    }
}

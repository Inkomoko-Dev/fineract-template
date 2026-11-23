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
package org.apache.fineract.accounting.provisioning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.accounting.provisioning.data.LoanData;
import org.apache.fineract.accounting.provisioning.data.LoanProductProvisioningEntryData;
import org.apache.fineract.accounting.provisioning.service.ProvisioningEntriesReadPlatformServiceImpl;
import org.apache.fineract.accounting.provisioning.service.ProvisioningEntriesReadPlatformServiceImpl.ProvisioningEntryLoanRow;
import org.junit.jupiter.api.Test;

class ProvisioningEntriesPaginationTest {

    @Test
    void groupsLoanRowsByEntryIdAndKeepsEntryOrder() {
        final List<ProvisioningEntryLoanRow> flat = new ArrayList<>();
        flat.add(row(10L, loan(1L)));
        flat.add(row(10L, loan(2L)));
        flat.add(row(11L, loan(3L)));
        flat.add(row(12L, loan(4L)));
        flat.add(row(12L, loan(5L)));
        flat.add(row(12L, loan(6L)));

        final List<LoanProductProvisioningEntryData> grouped = ProvisioningEntriesReadPlatformServiceImpl.groupProvisioningEntryRows(flat);

        assertEquals(3, grouped.size());
        assertEquals(2, grouped.get(0).getLoans().size());
        assertEquals(1, grouped.get(1).getLoans().size());
        assertEquals(3, grouped.get(2).getLoans().size());
        assertEquals(1L, grouped.get(0).getLoans().get(0).getId());
        assertEquals(3L, grouped.get(1).getLoans().get(0).getId());
        assertEquals(4L, grouped.get(2).getLoans().get(0).getId());
    }

    private static ProvisioningEntryLoanRow row(Long entryId, LoanData loan) {
        final List<LoanData> loans = new ArrayList<>();
        loans.add(loan);
        final LoanProductProvisioningEntryData data = new LoanProductProvisioningEntryData(1L, 1L, "HQ", "KES", 1L, "Product", 1L, "STD",
                "STANDARD", "PROVISION_BUCKET", 0L, BigDecimal.TEN, 1L, "L1", "Liability", 2L, "E1", "Expense", 8L, 11L, 2, 100L, loans);
        return new ProvisioningEntryLoanRow(entryId, data);
    }

    private static LoanData loan(Long id) {
        return new LoanData(id, "ACC-" + id, BigDecimal.valueOf(1000), BigDecimal.TEN);
    }
}

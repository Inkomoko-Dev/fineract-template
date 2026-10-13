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
package org.apache.fineract.accounting.provisioning.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ProvisionBatchJournalDataTest {

    @Test
    void originalJournalUsesMappedOrganizationName() {
        assertEquals("Inkomoko Kenya Capital",
                ProvisionBatchJournalData.journalDisplayName("Inkomoko Kenya Capital", false));
        assertEquals("Inkomoko Kenya Capital", journal("Inkomoko Kenya Capital", false).getName());
    }

    @Test
    void reversalJournalAppendsReversalToMappedOrganizationName() {
        assertEquals("Inkomoko Kenya Capital reversal",
                ProvisionBatchJournalData.journalDisplayName("Inkomoko Kenya Capital", true));
        assertEquals("Inkomoko Kenya Capital reversal", journal("Inkomoko Kenya Capital", true).getName());
    }

    @Test
    void reversalDoesNotDuplicateSuffix() {
        assertEquals("Inkomoko Kenya Capital reversal",
                ProvisionBatchJournalData.journalDisplayName("Inkomoko Kenya Capital reversal", true));
    }

    @Test
    void blankOrganizationName() {
        assertNull(ProvisionBatchJournalData.journalDisplayName(null, false));
        assertEquals("reversal", ProvisionBatchJournalData.journalDisplayName(null, true));
        assertEquals("reversal", ProvisionBatchJournalData.journalDisplayName("  ", true));
    }

    private static ProvisionBatchJournalData journal(final String organizationName, final boolean reversal) {
        return new ProvisionBatchJournalData(1L, 2L, "PROV", "J1", "ref", LocalDate.of(2026, 9, 30), "KES", 2L,
                organizationName, reversal, "CREATED", null, null, null, 271L, null, null, null, null);
    }
}

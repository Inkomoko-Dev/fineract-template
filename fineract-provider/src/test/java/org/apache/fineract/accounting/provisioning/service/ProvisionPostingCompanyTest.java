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
package org.apache.fineract.accounting.provisioning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProvisionPostingCompanyTest {

    @Test
    void extractsRwandaCapitalFromWatchGl() {
        assertEquals("Rwanda Capital", ProvisionPostingCompany.fromGlAccountCode(
                "2601-3591-Rwanda Capital_Watch/Special Mention-loan loss allowance", 73L));
    }

    @Test
    void extractsChadFromDoubtfulGl() {
        assertEquals("Chad", ProvisionPostingCompany.fromGlAccountCode(
                "2605-6225-Chad Doubtful-loan loss allowance", 117L));
    }

    @Test
    void extractsChadFromCollectiveGl() {
        assertEquals("Chad", ProvisionPostingCompany.fromGlAccountCode(
                "6306-4061-Chad Collective Credit loss provision", 117L));
    }

    @Test
    void extractsSouthSudanFromWatchGl() {
        assertEquals("South Sudan", ProvisionPostingCompany.fromGlAccountCode(
                "2601-3650-South Sudan_Watch/Special Mention-loan loss allowance", 49L));
    }

    @Test
    void extractsEthiopiaFromMappedGl() {
        assertEquals("Ethiopia", ProvisionPostingCompany.fromGlAccountCode(
                "2601-3649-Ethiopia_Watch/Special Mention-loan loss allowance", 5L));
    }

    @Test
    void fallsBackToOfficeWhenGlIsNotMapped() {
        assertEquals("OFFICE-73", ProvisionPostingCompany.fromGlAccountCode("2601", 73L));
    }

    @Test
    void skipsEthiopiaCompany() {
        assertTrue(ProvisionPostingCompany.isExcluded("Ethiopia"));
        assertTrue(ProvisionPostingCompany.isExcluded("Inkomoko - Ethiopia"));
        assertFalse(ProvisionPostingCompany.isExcluded("Rwanda Capital"));
        assertFalse(ProvisionPostingCompany.isExcluded("Chad"));
    }

    @Test
    void slugsCompanyForJournalReference() {
        assertEquals("RwandaCapital", ProvisionPostingCompany.slug("Rwanda Capital"));
        assertEquals("SouthSudan", ProvisionPostingCompany.slug("South Sudan"));
        assertEquals("KenyaCapital", ProvisionPostingCompany.slug("Kenya Capital"));
    }
}

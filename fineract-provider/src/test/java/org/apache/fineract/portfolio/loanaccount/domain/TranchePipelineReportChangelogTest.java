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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class TranchePipelineReportChangelogTest {

    @Test
    public void pipelineReportUsesTheSurveyAlreadyStoredByTheExternalApplication() throws Exception {
        final String xml = Files.readString(changelog());
        assertTrue(xml.contains("report_name\" value=\"Multi-tranche pipeline\""));
        assertTrue(xml.contains("report_name\" value=\"Multi-tranche amounts\""));
        assertTrue(xml.contains("AS loanId"));
        assertTrue(xml.contains("AS clientId"));
        assertTrue(xml.contains("AS country"));
        assertTrue(xml.contains("AS entity"));
        assertTrue(xml.contains("AS approvedPrincipal"));
        assertTrue(xml.contains("AS firstTrancheDisbursedAmount"));
        assertTrue(xml.contains("AS firstRepaymentDueAmount"));
        assertTrue(xml.contains("AS outstandingPrincipal"));
        assertTrue(xml.contains("AS totalLoanBalance"));
        assertTrue(xml.contains("AS generatedBy"));
        assertTrue(xml.contains("AS asOfDate"));
        assertTrue(xml.contains("AS filterFromDate"));
        assertTrue(xml.contains("loan_sub_status_id = 430"));
        assertTrue(xml.contains("READ_Multi-tranche pipeline"));
        assertTrue(xml.contains("READ_Multi-tranche eligibility"));
        assertTrue(xml.contains("READ_Multi-tranche amounts"));
        assertFalse(xml.contains("release_approved"));
        assertFalse(xml.contains("APPROVE_TRANCHERELEASE"));
        assertFalse(xml.contains("READ_TRANCHEAUDIT"));
    }

    private static Path changelog() {
        final String relative = "src/main/resources/db/changelog/tenant/custom-changelog/CGLT-832-pipeline-reporting.xml";
        final Path fromModule = Path.of(relative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return Path.of("fineract-provider", relative);
    }
}

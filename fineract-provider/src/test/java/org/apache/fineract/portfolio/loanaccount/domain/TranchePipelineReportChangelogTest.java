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
    public void pipelineReportIsTheOnlyMultiTrancheReportLeft() throws Exception {
        final String xml = Files.readString(changelog());
        assertTrue(xml.contains("report_name\" value=\"Multi-tranche pipeline Report\""));
        assertTrue(xml.contains("DELETE FROM stretchy_report"));
        assertTrue(xml.contains("WHERE report_name IN ('Multi-tranche eligibility', 'Multi-tranche pipeline', 'Multi-tranche amounts')"));
        assertTrue(xml.contains("DELETE FROM m_permission"));
        assertTrue(xml.contains("DELETE rp FROM m_role_permission rp"));
        assertFalse(xml.contains("report_name\" value=\"Multi-tranche eligibility\""));
        assertFalse(xml.contains("report_name\" value=\"Multi-tranche pipeline\""));
        assertFalse(xml.contains("report_name\" value=\"Multi-tranche amounts\""));
        assertFalse(xml.contains("READ_Multi-tranche pipeline\""));
    }

    @Test
    public void pipelineReportReturnsTheRequestedHeadersInOrder() throws Exception {
        final String xml = Files.readString(changelog());
        final String[] headers = { "Loan ID", "Loan Account Number", "Loan Status", "Client ID", "Client Name", "Office",
                "Loan Officer", "Product Name", "Approved Principal Amount", "Outstanding Principal", "Total Loan Balance",
                "Total number of Tranches", "Tranche Status", "First-Tranche Date", "First-Tranche Disbursed Amount",
                "First Repayment Due Date", "First Repayment Amount", "First Repayment Received Date",
                "First Repayment Received Amount", "BGS Verification Status (Operational & Funding Need)",
                "Investment Verification Status (Operational & Funding Need)", "Second-Tranche Eligibility Status",
                "Second-Tranche Approved Amount", "Second-Tranche Deadline Date", "Manual Review?", "Manual Action",
                "Manual review Approver", "Actual Second-Tranche Disbursement Date" };
        int previous = -1;
        for (final String header : headers) {
            final int position = xml.indexOf("AS `" + header + "`");
            assertTrue(position > previous, header + " is missing or out of order");
            previous = position;
        }
        assertTrue(xml.contains("COUNT(*) AS tranche_count"));
        assertTrue(xml.contains("COALESCE(tranche_total.tranche_count, 0) AS tranche_count"));
        assertTrue(xml.contains("${currentDate}"));
        assertFalse(xml.contains("${currentUserId}"));
        assertFalse(xml.contains("AS `Client ID & Client Name`"));
        assertFalse(xml.contains("AS `As-of Date`"));
        assertFalse(xml.contains("AS `Tranche Pipeline Status`"));
        assertFalse(xml.contains("AS `Approval Status`"));
        assertFalse(xml.contains("AS `Approver`"));
        assertFalse(xml.contains("filterFromDate"));
    }

    @Test
    public void pipelineReportBindsTheRequestedFilters() throws Exception {
        final String xml = Files.readString(changelog());
        assertTrue(xml.contains("'OfficeIdSelectOne'"));
        assertTrue(xml.contains("'loanOfficerIdSelectAll'"));
        assertTrue(xml.contains("'loanProductIdSelectAllWithoutCurrency'"));
        assertTrue(xml.contains("'tranchePipelineStatusSelectAll'"));
        assertTrue(xml.contains("'LoanAccountStatus'"));
        assertFalse(xml.contains("parameter_name\" value=\"trancheLoanStatusSelectAll\""));
        assertTrue(xml.contains("oldp.parameter_name = 'trancheLoanStatusSelectAll'"));
        assertTrue(xml.contains("'trancheDeadlineProximitySelectAll'"));
        assertTrue(xml.contains("'startDateSelect'"));
        assertTrue(xml.contains("'endDateSelect'"));
        assertFalse(xml.contains("parameter_name\" value=\"tranchePipelineStartDate\""));
        assertFalse(xml.contains("parameter_name\" value=\"tranchePipelineEndDate\""));
        assertTrue(xml.contains("oldp.parameter_name = 'tranchePipelineStartDate'"));
        assertTrue(xml.contains("oldp.parameter_name = 'tranchePipelineEndDate'"));
        assertFalse(xml.contains("2000-01-01"));
        assertFalse(xml.contains("2100-12-31"));
        assertTrue(xml.contains("CGLT-832-pipeline-report-reuse-filters"));
        assertTrue(xml.contains("READ_Multi-tranche pipeline Report"));
        assertTrue(xml.contains("WHEN ml.loan_sub_status_id = 420 AND second_plan.id IS NOT NULL THEN 1"));
        assertTrue(xml.contains("WHEN review.cancelled = 1 OR ml.loan_sub_status_id = 460 THEN 8"));
        assertTrue(xml.contains("WHEN ml.loan_sub_status_id = 470 THEN 9"));
        assertFalse(xml.contains("WHEN ml.loan_sub_status_id = 420 AND second_plan.id IS NOT NULL THEN 2"));
        assertTrue(xml.contains("AND (ml.loan_officer_id = ${loanOfficerId} OR ${loanOfficerId} IN (-1, -10, 0))"));
        assertTrue(xml.contains("pipeline.eligibility_deadline"));
    }

    @Test
    public void pipelineReportReadsTrancheMilestonesAndDisbursementDetails() throws Exception {
        final String xml = Files.readString(changelog());
        assertTrue(xml.contains("m_loan_tranche_milestone"));
        assertTrue(xml.contains("m_loan_disbursement_detail"));
        assertTrue(xml.contains("m_loan_repayment_schedule"));
        assertTrue(xml.contains("COALESCE(mile.eligibility_deadline_override, first_due.duedate) AS eligibility_deadline"));
        assertTrue(xml.contains("mile.disbursement_detail_id = chosen.disbursement_detail_id"));
        assertTrue(xml.contains("COALESCE(TRIM(m.submission_id), '') <> ''"));
        assertTrue(xml.contains("ORDER BY d.expected_disburse_date IS NULL, d.expected_disburse_date, d.id"));
        assertTrue(xml.contains("CGLT-832-pipeline-report-columns"));
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

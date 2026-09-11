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
package org.apache.fineract.portfolio.loanclassification.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationArrearsBand;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationOutcome;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoanClassificationJobService {

    static final int MAX_ERROR_LOG_LINES = 100;

    private final LoanClassificationCandidateAssembler candidateAssembler;
    private final LoanClassificationWritePlatformServiceImpl writePlatformService;
    private final JdbcTemplate jdbcTemplate;

    @CronTarget(jobName = JobName.CLASSIFY_LOANS)
    public void classifyLoans() {
        final OffsetDateTime jobStart = DateUtils.getOffsetDateTimeOfTenant();
        expireOverridesFromPreviousRuns(jobStart);
        final Map<Long, List<LoanClassificationArrearsBand>> bandCache = new HashMap<>();
        long lastLoanId = 0L;
        int processed = 0;
        int classified = 0;
        int flagged = 0;
        int skippedOverride = 0;
        int failed = 0;
        final List<String> errors = new ArrayList<>();
        while (true) {
            final List<LoanClassificationCandidateAssembler.Candidate> batch = this.candidateAssembler.loadPage(lastLoanId,
                    LoanClassificationCandidateAssembler.BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            final long batchStarted = System.currentTimeMillis();
            for (final LoanClassificationCandidateAssembler.Candidate candidate : batch) {
                lastLoanId = candidate.getLoanId();
                processed++;
                try {
                    final LoanClassificationOutcome outcome = this.writePlatformService.applyComputed(candidate,
                            LoanClassificationOutcome.SOURCE_AUTO, null, DateUtils.getOffsetDateTimeOfTenant(), false, bandCache);
                    if (outcome == null) {
                        skippedOverride++;
                    } else if (outcome.isValid()) {
                        classified++;
                    } else {
                        flagged++;
                        addError(errors, "loanId=" + candidate.getLoanId() + " status=" + outcome.getStatus() + " error="
                                + outcome.getErrorMessage());
                    }
                } catch (final Exception ex) {
                    failed++;
                    log.error("Loan classification failed for loan {} and will continue with the rest of the batch", candidate.getLoanId(),
                            ex);
                    addError(errors, "loanId=" + candidate.getLoanId() + " exception=" + ex.getMessage());
                }
            }
            final long elapsed = System.currentTimeMillis() - batchStarted;
            if (elapsed >= 2000) {
                log.warn("Loan classification batch ending at loan {} took {} ms (requirement is < 2000 ms per batch of {})", lastLoanId,
                        elapsed, LoanClassificationCandidateAssembler.BATCH_SIZE);
            } else {
                log.info("Loan classification batch ending at loan {} processed {} loans in {} ms", lastLoanId, batch.size(), elapsed);
            }
        }
        final String errorLog = toErrorLog(errors, flagged + failed);
        this.jdbcTemplate.update(
                "INSERT INTO m_loan_classification_job_run (started_on_utc, completed_on_utc, processed_count, classified_count, flagged_count, skipped_override_count, error_log) VALUES (?, ?, ?, ?, ?, ?, ?)",
                java.sql.Timestamp.from(jobStart.toInstant()), java.sql.Timestamp.from(DateUtils.getOffsetDateTimeOfTenant().toInstant()),
                processed, classified, flagged, skippedOverride, errorLog);
        if (errorLog != null) {
            log.error("Loan classification run completed with flagged or failed loans ({} flagged, {} failed):\n{}", flagged, failed,
                    errorLog);
        } else {
            log.info("Loan classification run processed {} loans ({} classified, {} flagged, {} skipped due to manual override, {} failed)",
                    processed, classified, flagged, skippedOverride, failed);
        }
    }

    static void addError(final List<String> errors, final String line) {
        if (errors.size() < MAX_ERROR_LOG_LINES) {
            errors.add(line);
        }
    }

    static String toErrorLog(final List<String> errors, final int totalIssues) {
        if (errors.isEmpty()) {
            return null;
        }
        if (totalIssues > errors.size()) {
            errors.add("... and " + (totalIssues - errors.size()) + " more");
        }
        return String.join("\n", errors);
    }

    private void expireOverridesFromPreviousRuns(final OffsetDateTime jobStart) {
        final int expired = this.jdbcTemplate.update(
                "UPDATE m_loan_classification SET override_active = FALSE WHERE override_active = TRUE AND (override_on_utc IS NULL OR override_on_utc < ?)",
                java.sql.Timestamp.from(jobStart.toInstant()));
        log.info("Expired {} loan classification overrides from previous runs", expired);
    }
}

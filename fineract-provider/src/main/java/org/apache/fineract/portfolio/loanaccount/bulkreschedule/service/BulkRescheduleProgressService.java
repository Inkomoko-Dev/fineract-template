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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Short transactions used to publish execution state while the background worker is running. */
@Service
@RequiredArgsConstructor
public class BulkRescheduleProgressService {

    private static final long LEASE_MINUTES = 5;

    private final BulkRescheduleExecutionRepository executionRepository;
    private final BulkRescheduleResultRepository resultRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult claim(final Long executionId, final String workerToken) {
        final var now = DateUtils.getLocalDateTimeOfSystem();
        final var leaseExpiresAt = now.plusMinutes(LEASE_MINUTES);
        if (executionRepository.claimApproved(executionId, BulkRescheduleExecutionStatus.APPROVED,
                BulkRescheduleExecutionStatus.EXECUTING, workerToken, leaseExpiresAt) == 1) {
            return ClaimResult.INITIAL;
        }
        if (executionRepository.claimExpired(executionId, BulkRescheduleExecutionStatus.EXECUTING, workerToken, leaseExpiresAt,
                now) == 1) {
            return ClaimResult.RECOVERED;
        }
        return ClaimResult.NONE;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewLease(final Long executionId, final String workerToken) {
        return executionRepository.renewLease(executionId, BulkRescheduleExecutionStatus.EXECUTING, workerToken,
                DateUtils.getLocalDateTimeOfSystem().plusMinutes(LEASE_MINUTES)) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshCounts(final Long executionId, final int executionFailures, final String workerToken) {
        executionRepository.findById(executionId).ifPresent(execution -> {
            if (!workerToken.equals(execution.getWorkerToken())) {
                return;
            }
            execution.setTotalSucceeded((int) resultRepository.countByExecutionIdAndStatus(executionId, BulkRescheduleResultStatus.SUCCEEDED));
            execution.setTotalFailed((int) resultRepository.countByExecutionIdAndStatus(executionId, BulkRescheduleResultStatus.FAILED));
            execution.setTotalExecutionFailed(executionFailures);
            execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
            executionRepository.save(execution);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(final Long executionId, final int executionFailures, final String workerToken) {
        executionRepository.findById(executionId).ifPresent(execution -> {
            if (!workerToken.equals(execution.getWorkerToken())) {
                return;
            }
            final int succeeded = (int) resultRepository.countByExecutionIdAndStatus(executionId, BulkRescheduleResultStatus.SUCCEEDED);
            final int failed = (int) resultRepository.countByExecutionIdAndStatus(executionId, BulkRescheduleResultStatus.FAILED);
            execution.setTotalSucceeded(succeeded);
            execution.setTotalFailed(failed);
            execution.setTotalExecutionFailed(executionFailures);
            execution.setStatus(executionFailures == 0 ? BulkRescheduleExecutionStatus.COMPLETED
                    : succeeded == 0 ? BulkRescheduleExecutionStatus.FAILED : BulkRescheduleExecutionStatus.PARTIAL_SUCCESS);
            execution.setExecutionCompletedAt(DateUtils.getLocalDateTimeOfSystem());
            execution.setWorkerToken(null);
            execution.setLeaseExpiresAt(null);
            execution.setLastHeartbeatAt(null);
            execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
            executionRepository.save(execution);
        });
    }

    public enum ClaimResult {
        NONE,
        INITIAL,
        RECOVERED
    }
}

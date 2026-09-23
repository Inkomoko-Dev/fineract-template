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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.handler;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.persistence.AfterCommitExecutor;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleAsyncExecutionService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@CommandType(entity = "RESCHEDULELOAN", action = "RECOVER")
public class RecoverBulkRescheduleCommandHandler implements NewCommandSourceHandler {

    private final PlatformSecurityContext securityContext;
    private final BulkRescheduleExecutionRepository executionRepository;
    private final BulkRescheduleResultRepository resultRepository;
    private final OfficeHierarchyService officeHierarchyService;
    private final BulkRescheduleAsyncExecutionService asyncExecutionService;

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        final var user = securityContext.authenticatedUser();
        final var execution = executionRepository.findById(command.entityId())
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.not.found",
                        "Execution not found with ID: " + command.entityId()));
        if (!canResume(user, execution)) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.permission.denied",
                    "Only the request creator or a user with bulk reschedule rights can resume this execution");
        }
        if (!officeHierarchyService.validateUserAccessToOffice(user, execution.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.office.denied",
                    "User does not have access to this bulk reschedule office");
        }
        final var status = execution.getStatus();
        final boolean previewing = status == BulkRescheduleExecutionStatus.PREVIEWING;
        final boolean rollingBack = status == BulkRescheduleExecutionStatus.ROLLING_BACK;
        final boolean executing = status == BulkRescheduleExecutionStatus.EXECUTING;
        final boolean incomplete = status == BulkRescheduleExecutionStatus.FAILED
                || status == BulkRescheduleExecutionStatus.PARTIAL_SUCCESS;
        if (!executing && !incomplete && !rollingBack && !previewing) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.status.invalid",
                    "Only an interrupted or incomplete execution can be resumed");
        }
        if ((executing || rollingBack || previewing) && execution.getLeaseExpiresAt() != null
                && !execution.getLeaseExpiresAt().isBefore(DateUtils.getLocalDateTimeOfSystem())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.worker.active",
                    "The execution worker is still active; wait for its lease to expire before resuming");
        }
        if (previewing) {
            final long remainingPreview = resultRepository.countUnsnapshottedByExecutionId(execution.getId(),
                    BulkRescheduleResultStatus.PREVIEW_MATCHED);
            if (remainingPreview == 0) {
                throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.nothing.pending",
                        "There are no remaining loans to resume");
            }
            execution.setWorkerToken(null);
            execution.setLeaseExpiresAt(null);
            execution.setLastHeartbeatAt(null);
            execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
            executionRepository.save(execution);
            final var context = ThreadLocalContextUtil.getContext();
            AfterCommitExecutor.execute(() -> asyncExecutionService.submitPreview(execution.getId(), context));
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(execution.getId())
                    .withOfficeId(execution.getOfficeId()).build();
        }
        if (rollingBack) {
            final long remainingRollback = resultRepository.countByExecutionIdAndStatus(execution.getId(),
                    BulkRescheduleResultStatus.SUCCEEDED);
            if (remainingRollback == 0) {
                throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.nothing.pending",
                        "There are no remaining loans to resume");
            }
            execution.setWorkerToken(null);
            execution.setLeaseExpiresAt(null);
            execution.setLastHeartbeatAt(null);
            execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
            executionRepository.save(execution);
            final var context = ThreadLocalContextUtil.getContext();
            AfterCommitExecutor.execute(() -> asyncExecutionService.submitRollback(execution.getId(), context));
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(execution.getId())
                    .withOfficeId(execution.getOfficeId()).build();
        }
        resultRepository.resetUncommittedFailures(execution.getId(), BulkRescheduleResultStatus.FAILED,
                BulkRescheduleResultStatus.PREVIEW_MATCHED);
        final long remaining = resultRepository.countByExecutionIdAndStatus(execution.getId(),
                BulkRescheduleResultStatus.PREVIEW_MATCHED);
        if (remaining == 0) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.nothing.pending",
                    "There are no remaining loans to resume");
        }
        if (incomplete) {
            execution.setStatus(BulkRescheduleExecutionStatus.EXECUTING);
            execution.setExecutionError(null);
            execution.setExecutionCompletedAt(null);
            execution.setWorkerToken(null);
            execution.setLeaseExpiresAt(null);
            execution.setLastHeartbeatAt(null);
            execution.setUpdatedAt(DateUtils.getLocalDateTimeOfSystem());
            executionRepository.save(execution);
        }

        final var context = ThreadLocalContextUtil.getContext();
        AfterCommitExecutor.execute(() -> asyncExecutionService.submit(execution.getId(), context));
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(execution.getId())
                .withOfficeId(execution.getOfficeId()).build();
    }

    private boolean canResume(final AppUser user, final BulkRescheduleExecution execution) {
        if (execution.getUser() != null && execution.getUser().getId().equals(user.getId())) {
            return true;
        }
        return hasPermission(user, "APPROVE_RESCHEDULELOAN") || hasPermission(user, "CREATE_RESCHEDULELOAN")
                || hasPermission(user, "RECOVER_RESCHEDULELOAN");
    }

    private boolean hasPermission(final AppUser user, final String permission) {
        try {
            user.validateHasPermissionTo(permission);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}

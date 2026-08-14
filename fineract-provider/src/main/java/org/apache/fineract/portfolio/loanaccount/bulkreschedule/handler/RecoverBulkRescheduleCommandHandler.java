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
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleAsyncExecutionService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@CommandType(entity = "RESCHEDULELOAN", action = "RECOVER")
public class RecoverBulkRescheduleCommandHandler implements NewCommandSourceHandler {

    private final PlatformSecurityContext securityContext;
    private final BulkRescheduleExecutionRepository executionRepository;
    private final OfficeHierarchyService officeHierarchyService;
    private final BulkRescheduleAsyncExecutionService asyncExecutionService;

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        final var user = securityContext.authenticatedUser();
        user.validateHasPermissionTo("APPROVE_RESCHEDULELOAN");
        final var execution = executionRepository.findById(command.entityId())
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.not.found",
                        "Execution not found with ID: " + command.entityId()));
        if (execution.getStatus() != BulkRescheduleExecutionStatus.EXECUTING) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.status.invalid",
                    "Only an interrupted execution can be resumed");
        }
        if (execution.getApprover() == null || !execution.getApprover().getId().equals(user.getId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.approver.required",
                    "Only the assigned approver can resume this execution");
        }
        if (!officeHierarchyService.validateUserAccessToOffice(user, execution.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.office.denied",
                    "User does not have access to this bulk reschedule office");
        }
        if (execution.getLeaseExpiresAt() != null
                && !execution.getLeaseExpiresAt().isBefore(DateUtils.getLocalDateTimeOfSystem())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.recovery.worker.active",
                    "The execution worker is still active; wait for its lease to expire before resuming");
        }

        final var context = ThreadLocalContextUtil.getContext();
        AfterCommitExecutor.execute(() -> asyncExecutionService.submit(execution.getId(), context));
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(execution.getId())
                .withOfficeId(execution.getOfficeId()).build();
    }
}

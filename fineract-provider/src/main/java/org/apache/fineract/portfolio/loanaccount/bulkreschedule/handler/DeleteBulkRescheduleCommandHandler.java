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
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleAuditRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleExecutionRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.OfficeHierarchyService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@CommandType(entity = "RESCHEDULELOAN", action = "DELETE")
public class DeleteBulkRescheduleCommandHandler implements NewCommandSourceHandler {

    private final PlatformSecurityContext securityContext;
    private final BulkRescheduleExecutionRepository executionRepository;
    private final BulkRescheduleResultRepository resultRepository;
    private final BulkRescheduleAuditRepository auditRepository;
    private final OfficeHierarchyService officeHierarchyService;

    @Override
    @Transactional
    public CommandProcessingResult processCommand(final JsonCommand command) {
        final Long executionId = command.entityId();
        final AppUser user = securityContext.authenticatedUser();
        final var execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.not.found",
                        "Execution not found with ID: " + executionId));
        if (execution.getStatus() != BulkRescheduleExecutionStatus.PREVIEW) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.delete.invalid.status",
                    "Only a bulk reschedule in PREVIEW status can be cancelled and deleted");
        }
        if (!officeHierarchyService.validateUserAccessToOffice(user, execution.getOfficeId())) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.execution.access.denied",
                    "User does not have access to this bulk reschedule execution");
        }
        if (!execution.getUser().getId().equals(user.getId()) && !hasCreatePermission(user)) {
            throw new GeneralPlatformDomainRuleException("error.msg.bulk.reschedule.delete.permission.denied",
                    "Only the initiator or a user with create permission can cancel this preview");
        }
        final Long officeId = execution.getOfficeId();
        resultRepository.deleteByExecutionId(executionId);
        auditRepository.deleteByExecutionId(executionId);
        resultRepository.flush();
        auditRepository.flush();
        executionRepository.delete(execution);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(executionId).withOfficeId(officeId)
                .build();
    }

    private boolean hasCreatePermission(final AppUser user) {
        try {
            user.validateHasPermissionTo("CREATE_RESCHEDULELOAN");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}

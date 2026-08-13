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
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleResponseDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.TemplateDataDto;
import org.springframework.stereotype.Service;

/**
 * Single facade for bulk reschedule use cases.
 *
 * This keeps the API and command handlers aligned to one service entry point while
 * reusing the existing repository-backed service implementations internally.
 */
@Service
@RequiredArgsConstructor
public class BulkRescheduleService {

    private final BulkRescheduleTemplateService templateService;
    private final BulkReschedulePreviewService previewService;
    private final BulkRescheduleApprovalService approvalService;
    private final BulkRescheduleExecutionService executionService;

    public TemplateDataDto getTemplateData(final Long officeId) {
        return this.templateService.getTemplateData(officeId);
    }

    public CommandProcessingResult performDryRun(final JsonCommand request) {
        return this.previewService.performDryRun(request);
    }

    public CommandProcessingResult approveExecution(final JsonCommand request) {
        return this.approvalService.approveExecution(request);
    }

    public CommandProcessingResult rejectExecution(final JsonCommand request) {
        return this.approvalService.rejectExecution(request);
    }

    public CommandProcessingResult rollbackExecution(JsonCommand jsonCommand) {
        final String reason = jsonCommand.stringValueOfParameterNamedAllowingNull("rollbackReason");
        final BulkRescheduleResponseDto response = this.executionService.rollbackExecution(jsonCommand.entityId(), reason);
        return new CommandProcessingResultBuilder().withCommandId(jsonCommand.commandId())
                .withEntityId(response.getExecutionId()).build();
    }
}

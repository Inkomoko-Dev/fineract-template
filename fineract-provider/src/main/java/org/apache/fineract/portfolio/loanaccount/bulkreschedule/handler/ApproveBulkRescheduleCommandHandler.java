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
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleService;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.service.BulkRescheduleAsyncExecutionService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@CommandType(entity = "RESCHEDULELOAN", action = "APPROVE")
public class ApproveBulkRescheduleCommandHandler implements NewCommandSourceHandler {

    private final BulkRescheduleService bulkRescheduleService;
    private final BulkRescheduleAsyncExecutionService asyncExecutionService;

    @Override
    public CommandProcessingResult processCommand(final JsonCommand jsonCommand) {

        final CommandProcessingResult result = bulkRescheduleService.approveExecution(jsonCommand);
        final var context = ThreadLocalContextUtil.getContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    asyncExecutionService.submit(jsonCommand.entityId(), context);
                }
            });
        } else {
            asyncExecutionService.submit(jsonCommand.entityId(), context);
        }
        return result;
    }

}

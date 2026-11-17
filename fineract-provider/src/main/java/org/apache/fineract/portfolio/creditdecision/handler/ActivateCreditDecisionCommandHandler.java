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
package org.apache.fineract.portfolio.creditdecision.handler;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.creditdecision.service.CreditDecisionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs when a checker approves, in the Checker Inbox, the task that activates a policy version. Nothing is activated
 * before that. The checker is the signed-in user, and the version still cannot be approved by whoever prepared,
 * edited or submitted it.
 */
@Service
@RequiredArgsConstructor
@CommandType(entity = "CREDITDECISION", action = "ACTIVATE")
public class ActivateCreditDecisionCommandHandler implements NewCommandSourceHandler {

    private final CreditDecisionService service;

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        // version number is a plain int in the task body; do not require a locale
        final Integer versionNo = command.integerValueSansLocaleOfParameterNamed("versionNo");
        if (command.entityId() == null || versionNo == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.task.incomplete",
                    "This task does not say which profile version to activate.");
        }
        service.approve(command.entityId(), versionNo);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(command.entityId()).build();
    }
}

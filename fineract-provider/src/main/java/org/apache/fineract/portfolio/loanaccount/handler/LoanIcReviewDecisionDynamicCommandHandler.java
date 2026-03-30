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
package org.apache.fineract.portfolio.loanaccount.handler;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.portfolio.loanaccount.service.LoanApplicationDecisionWritePlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dynamic IC Review Decision Command Handler.
 * This handler can process IC review decisions for any level (1, 2, 3, 4, 5, 6, 7, ...).
 * It replaces the need for separate handlers for each level.
 */
@Service
@RequiredArgsConstructor
public class LoanIcReviewDecisionDynamicCommandHandler {

    private final LoanApplicationDecisionWritePlatformService loanApplicationDecisionWritePlatformService;

    /**
     * Process IC review decision for any level.
     *
     * @param command the command containing loan ID and decision data
     * @param levelNumber the IC review level number (1, 2, 3, etc.)
     * @param isAccept true for accept, false for reject
     * @return the command processing result
     */
    @Transactional
    public CommandProcessingResult processIcReviewDecision(final JsonCommand command, Integer levelNumber, boolean isAccept) {
        if (isAccept) {
            return this.loanApplicationDecisionWritePlatformService.acceptIcReviewDecisionDynamic(command.getLoanId(), command, levelNumber);
        } else {
            return this.loanApplicationDecisionWritePlatformService.rejectIcReviewDecisionDynamic(command.getLoanId(), command, levelNumber);
        }
    }
}

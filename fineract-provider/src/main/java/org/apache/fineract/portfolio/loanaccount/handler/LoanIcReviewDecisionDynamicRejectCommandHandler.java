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
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.annotation.CommandType;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.loanaccount.service.LoanApplicationDecisionWritePlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command handler for dynamic IC Review Decision Reject.
 * This handler processes IC review decision rejections for levels 6 and above.
 * The level number is extracted from the command URL.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@CommandType(entity = "LOANICREVIEWDECISIONDYNAMIC", action = "REJECT")
public class LoanIcReviewDecisionDynamicRejectCommandHandler implements NewCommandSourceHandler {

    private final LoanApplicationDecisionWritePlatformService loanApplicationDecisionWritePlatformService;

    // Pattern to extract level number from href like /loans/decision/icReviewDecision/level/{levelNumber}/reject/{loanId}
    private static final Pattern LEVEL_PATTERN = Pattern.compile("/level/(\\d+)/");

    @Transactional
    @Override
    public CommandProcessingResult processCommand(final JsonCommand command) {
        Integer levelNumber = extractLevelNumber(command);
        log.debug("Processing dynamic IC review reject for level {} on loan {}", levelNumber, command.getLoanId());
        return this.loanApplicationDecisionWritePlatformService.rejectIcReviewDecisionDynamic(
                command.getLoanId(), command, levelNumber);
    }

    /**
     * Extracts the level number from the command URL.
     * Expected URL pattern: /loans/decision/icReviewDecision/level/{levelNumber}/reject/{loanId}
     */
    private Integer extractLevelNumber(JsonCommand command) {
        String url = command.getUrl();
        if (url == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.ic.review.level.number.not.found",
                    "IC Review level number could not be determined from the request URL");
        }

        Matcher matcher = LEVEL_PATTERN.matcher(url);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        throw new GeneralPlatformDomainRuleException("error.msg.ic.review.level.number.not.found",
                "IC Review level number could not be extracted from URL: " + url);
    }
}

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

import com.google.gson.JsonParser;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.exception.LoanNotFoundException;

final class TrancheMilestoneCommandResults {

    private TrancheMilestoneCommandResults() {}

    static String loanKey(final JsonCommand command) {
        final String url = command.getUrl();
        final String prefix = "/loans/";
        final int start = url == null ? -1 : url.indexOf(prefix);
        if (start < 0) {
            throw new LoanNotFoundException(url);
        }
        final int from = start + prefix.length();
        final int slash = url.indexOf('/', from);
        final String key = slash < 0 ? url.substring(from) : url.substring(from, slash);
        if (key.isBlank()) {
            throw new LoanNotFoundException(url);
        }
        return key;
    }

    static CommandProcessingResult result(final LoanRepository loanRepository, final String response) {
        final long loanId = JsonParser.parseString(response).getAsJsonObject().get("loanId").getAsLong();
        final Loan loan = loanRepository.findById(loanId).orElseThrow(() -> new LoanNotFoundException(loanId));
        return new CommandProcessingResultBuilder().withEntityId(loanId).withLoanId(loanId).withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId()).withOfficeId(loan.getOfficeId()).withResourceIdAsString(response).build();
    }
}

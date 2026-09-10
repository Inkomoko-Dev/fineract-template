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
package org.apache.fineract.portfolio.loanclassification.service;

import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.businessevent.BusinessEventListener;
import org.apache.fineract.portfolio.businessevent.domain.loan.LoanDisbursalBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.transaction.LoanUndoWrittenOffBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.transaction.LoanWrittenOffPostBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanclassification.data.LoanClassificationOutcome;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class LoanClassificationEventListener {

    private final BusinessEventNotifierService businessEventNotifierService;
    private final LoanClassificationWritePlatformService writePlatformService;

    @PostConstruct
    public void subscribe() {
        this.businessEventNotifierService.addPostBusinessEventListener(LoanWrittenOffPostBusinessEvent.class, new WrittenOffListener());
        this.businessEventNotifierService.addPostBusinessEventListener(LoanUndoWrittenOffBusinessEvent.class, new UndoWrittenOffListener());
        this.businessEventNotifierService.addPostBusinessEventListener(LoanDisbursalBusinessEvent.class, new DisbursalListener());
    }

    private final class WrittenOffListener implements BusinessEventListener<LoanWrittenOffPostBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanWrittenOffPostBusinessEvent event) {
            final Loan loan = event.get().getLoan();
            log.info("Updating loan classification to Write-off immediately for loan {}", loan.getId());
            LoanClassificationEventListener.this.writePlatformService.classifyWrittenOffLoan(loan.getId());
        }
    }

    private final class UndoWrittenOffListener implements BusinessEventListener<LoanUndoWrittenOffBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanUndoWrittenOffBusinessEvent event) {
            final Loan loan = event.get().getLoan();
            LoanClassificationEventListener.this.writePlatformService.classifyLoan(loan.getId(), LoanClassificationOutcome.SOURCE_AUTO);
        }
    }

    private final class DisbursalListener implements BusinessEventListener<LoanDisbursalBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanDisbursalBusinessEvent event) {
            final Loan loan = event.get();
            LoanClassificationEventListener.this.writePlatformService.classifyLoan(loan.getId(), LoanClassificationOutcome.SOURCE_AUTO);
        }
    }
}

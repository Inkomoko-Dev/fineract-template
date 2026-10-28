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
package org.apache.fineract.portfolio.loanaccount.excessrefund.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.springframework.stereotype.Service;

/**
 * Posts excess refunds to CBS via {@link LoanAccountDomainService#creditBalanceRefund}. Journals are produced only by
 * that path (Dr overpayment liability / Cr fund source per product and payment-type mapping). Do not add parallel cash
 * journals for Payment Hub payouts.
 */
@Service
@RequiredArgsConstructor
public class ExcessRefundGlPoster {

    private final LoanAccountDomainService loanAccountDomainService;
    private final PaymentTypeRepositoryWrapper paymentTypeRepository;

    public Long post(final Long loanId, final LocalDate transactionDate, final BigDecimal amount, final String note,
            final String externalId) {
        return post(loanId, transactionDate, amount, note, externalId, null);
    }

    public Long post(final Long loanId, final LocalDate transactionDate, final BigDecimal amount, final String note,
            final String externalId, final Long paymentTypeId) {
        PaymentDetail paymentDetail = null;
        if (paymentTypeId != null) {
            final PaymentType paymentType = this.paymentTypeRepository.findOneWithNotFoundDetection(paymentTypeId);
            paymentDetail = PaymentDetail.instance(paymentType, null, null, null, null, null);
        }
        final CommandProcessingResult result = this.loanAccountDomainService
                .creditBalanceRefund(loanId, transactionDate, amount, note, externalId, paymentDetail).build();
        return result.getEntityId();
    }
}

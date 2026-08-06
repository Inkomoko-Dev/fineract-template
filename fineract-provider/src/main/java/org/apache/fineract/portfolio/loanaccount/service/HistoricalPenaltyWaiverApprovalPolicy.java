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
package org.apache.fineract.portfolio.loanaccount.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverApprovalRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** CGLT-656: a large waiver or an old penalty needs a second pair of eyes. Both thresholds are configuration. */
@Component
public class HistoricalPenaltyWaiverApprovalPolicy {

    private final ConfigurationDomainService configurationDomainService;

    @Autowired
    public HistoricalPenaltyWaiverApprovalPolicy(final ConfigurationDomainService configurationDomainService) {
        this.configurationDomainService = configurationDomainService;
    }

    public HistoricalPenaltyWaiverApprovalRequirement determine(final BigDecimal waiverAmount, final LocalDate chargeDueDate,
            final LocalDate businessDate) {

        // A null threshold means the configuration row is disabled, which switches that trigger off entirely.
        final Long amountThreshold = this.configurationDomainService.retrieveHistoricalPenaltyWaiverApprovalAmountThreshold();
        final Long ageThresholdDays = this.configurationDomainService.retrieveHistoricalPenaltyWaiverApprovalAgeDays();

        final boolean amountBreached = amountThreshold != null && waiverAmount != null
                && waiverAmount.compareTo(BigDecimal.valueOf(amountThreshold)) > 0;

        final boolean ageBreached = ageThresholdDays != null && chargeDueDate != null
                && ChronoUnit.DAYS.between(chargeDueDate, businessDate) > ageThresholdDays;

        return HistoricalPenaltyWaiverApprovalRequirement.of(amountBreached, ageBreached);
    }
}

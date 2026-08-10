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
package org.apache.fineract.portfolio.loanproduct.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductData;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductChargeData;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductData;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
public class ThirdPartyDisbursementProductMapper {

    public ThirdPartyDisbursementProductData toPartnerData(final LoanProductData product) {
        return new ThirdPartyDisbursementProductData(product.getId(), product.getName(), product.getDescription(),
                product.getStartDate(), product.getCloseDate(),
                product.getEnableThirdPartyDisbursement(), product.getCurrency(), product.getPrincipal(), product.getMinPrincipal(),
                product.getMaxPrincipal(), product.getNumberOfRepayments(), product.getMinNumberOfRepayments(),
                product.getMaxNumberOfRepayments(), product.getRepaymentEvery(), product.getRepaymentFrequencyType(),
                product.getInterestRatePerPeriod(), product.getMinInterestRatePerPeriod(), product.getMaxInterestRatePerPeriod(),
                product.getInterestRateFrequencyType(), product.getAnnualInterestRate(), product.getAmortizationType(),
                product.getInterestType(), product.getInterestCalculationPeriodType(), product.getGraceOnPrincipalPayment(),
                product.getGraceOnInterestPayment(), product.getGraceOnInterestCharged(), product.getGraceOnArrearsAgeing(),
                product.getTransactionProcessingStrategyName(), product.getBnplLoanProduct(), product.getRequiresEquityContribution(),
                product.getEquityContributionLoanPercentage(), mapCharges(product.getChargeOptions()));
    }

    private List<ThirdPartyDisbursementProductChargeData> mapCharges(final Collection<ChargeData> charges) {
        if (CollectionUtils.isEmpty(charges)) {
            return Collections.emptyList();
        }
        return charges.stream().map(this::mapCharge).collect(Collectors.toList());
    }

    private ThirdPartyDisbursementProductChargeData mapCharge(final ChargeData charge) {
        final String currencyCode = charge.getCurrency() == null ? null : charge.getCurrency().getCode();
        return new ThirdPartyDisbursementProductChargeData(charge.getId(), charge.getName(), charge.getChargeTimeType(),
                charge.getChargeCalculationType(), charge.getAmount(), currencyCode);
    }
}

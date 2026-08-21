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

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.portfolio.client.domain.ClientOtherInfo;
import org.apache.fineract.portfolio.client.domain.ClientOtherInfoRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientBankDetailsResolver {

    private final ClientOtherInfoRepository clientOtherInfoRepository;

    public ResolvedClientPaymentDetails resolve(final Long clientId, final Integer paymentTo, final String clientPhoneNumber,
            final String clientAccountNumber, final String clientBankName) {

        final ResolvedClientPaymentDetails supplied = new ResolvedClientPaymentDetails(clientPhoneNumber, clientAccountNumber,
                clientBankName);

        if (clientId == null) {
            return supplied;
        }
        if (LoanDisbursementDetails.PaymentToType.SUPPLIER.getValue() == (paymentTo == null ? 0 : paymentTo.intValue())) {
            return supplied;
        }
        if (StringUtils.isNotBlank(clientPhoneNumber) && StringUtils.isNotBlank(clientAccountNumber)
                && StringUtils.isNotBlank(clientBankName)) {
            return supplied;
        }

        final ClientOtherInfo clientOtherInfo = this.clientOtherInfoRepository.getByClientId(clientId);
        if (clientOtherInfo == null) {
            return supplied;
        }

        return new ResolvedClientPaymentDetails(
                StringUtils.defaultIfBlank(clientPhoneNumber, StringUtils.trimToNull(clientOtherInfo.getTelephoneNo())),
                StringUtils.defaultIfBlank(clientAccountNumber, StringUtils.trimToNull(clientOtherInfo.getBankAccountNumber())),
                StringUtils.defaultIfBlank(clientBankName, storedBankName(clientOtherInfo)));
    }

    private String storedBankName(final ClientOtherInfo clientOtherInfo) {
        if (clientOtherInfo.getBank() != null && StringUtils.isNotBlank(clientOtherInfo.getBank().getBankName())) {
            return StringUtils.trimToNull(clientOtherInfo.getBank().getBankName());
        }
        return StringUtils.trimToNull(clientOtherInfo.getBankName());
    }

    public static final class ResolvedClientPaymentDetails {

        private final String clientPhoneNumber;
        private final String clientAccountNumber;
        private final String clientBankName;

        public ResolvedClientPaymentDetails(final String clientPhoneNumber, final String clientAccountNumber,
                final String clientBankName) {
            this.clientPhoneNumber = clientPhoneNumber;
            this.clientAccountNumber = clientAccountNumber;
            this.clientBankName = clientBankName;
        }

        public String getClientPhoneNumber() {
            return this.clientPhoneNumber;
        }

        public String getClientAccountNumber() {
            return this.clientAccountNumber;
        }

        public String getClientBankName() {
            return this.clientBankName;
        }
    }
}

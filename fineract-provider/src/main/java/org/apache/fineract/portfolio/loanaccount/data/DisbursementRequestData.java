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

package org.apache.fineract.portfolio.loanaccount.data;

import java.math.BigDecimal;

public class DisbursementRequestData {

    private final String requestId;
    private final String externalId;
    private final BigDecimal amount;
    private final String currencyCode;
    private final String paymentMethod;
    private final String clientPhoneNumber;
    private final String clientAccountNumber;
    private final String clientBankName;

    private final String origin;

    private final Long paymentTypeId;

    public DisbursementRequestData(String requestId, String loanAccount, BigDecimal amount, String currencyCode, String paymentMethod,
            String clientPhoneNumber, String clientAccountNumber, String clientBankName, String origin, Long paymentTypeId) {
        this.requestId = requestId;
        this.externalId = loanAccount;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.paymentMethod = paymentMethod;
        this.clientPhoneNumber = clientPhoneNumber;
        this.clientAccountNumber = clientAccountNumber;
        this.clientBankName = clientBankName;
        this.origin = origin;
        this.paymentTypeId = paymentTypeId;
        //country/office
        //client name
    }

    public String getRequestId() {
        return requestId;
    }

    public String getLoanAccount() {
        return externalId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getClientPhoneNumber() {
        return clientPhoneNumber;
    }

    public String getClientAccountNumber() {
        return clientAccountNumber;
    }

    public String getClientBankName() {
        return clientBankName;
    }

    public String getOrigin() {
        return origin;
    }
}

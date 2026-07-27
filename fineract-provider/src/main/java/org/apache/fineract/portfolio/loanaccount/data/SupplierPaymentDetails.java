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

import lombok.Getter;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;

@Getter
public class SupplierPaymentDetails {

    private final PaymentType paymentType;
    private final String phoneNumber;
    private final String accountNumber;
    private final String bankName;
    private final String accountName;
    private final String beneficiaryName;

    public SupplierPaymentDetails(final PaymentType paymentType, final String phoneNumber, final String accountNumber,
            final String bankName, final String accountName, final String beneficiaryName) {
        this.paymentType = paymentType;
        this.phoneNumber = phoneNumber;
        this.accountNumber = accountNumber;
        this.bankName = bankName;
        this.accountName = accountName;
        this.beneficiaryName = beneficiaryName;
    }
}

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
import java.time.LocalDate;

/**
 * Active-loan row used to fill the bulk repayment upload template.
 */
public class LoanRepaymentTemplateData {

    private final String accountNo;
    private final String statusValue;
    private final Long clientId;
    private final String clientName;
    private final String clientExternalId;
    private final String productName;
    private final BigDecimal principal;
    private final BigDecimal totalOutstanding;
    private final LocalDate disbursementDate;
    private final String officeName;
    private final String mfiCode;

    public LoanRepaymentTemplateData(final String accountNo, final String statusValue, final Long clientId, final String clientName,
            final String clientExternalId, final String productName, final BigDecimal principal, final BigDecimal totalOutstanding,
            final LocalDate disbursementDate, final String officeName, final String mfiCode) {
        this.accountNo = accountNo;
        this.statusValue = statusValue;
        this.clientId = clientId;
        this.clientName = clientName;
        this.clientExternalId = clientExternalId;
        this.productName = productName;
        this.principal = principal;
        this.totalOutstanding = totalOutstanding;
        this.disbursementDate = disbursementDate;
        this.officeName = officeName;
        this.mfiCode = mfiCode;
    }

    public String getAccountNo() {
        return this.accountNo;
    }

    public String getStatusValue() {
        return this.statusValue;
    }

    public Long getClientId() {
        return this.clientId;
    }

    public String getClientName() {
        return this.clientName;
    }

    public String getClientExternalId() {
        return this.clientExternalId;
    }

    public String getProductName() {
        return this.productName;
    }

    public BigDecimal getPrincipal() {
        return this.principal;
    }

    public BigDecimal getTotalOutstanding() {
        return this.totalOutstanding;
    }

    public LocalDate getDisbursementDate() {
        return this.disbursementDate;
    }

    public String getOfficeName() {
        return this.officeName;
    }

    public String getMfiCode() {
        return this.mfiCode;
    }
}

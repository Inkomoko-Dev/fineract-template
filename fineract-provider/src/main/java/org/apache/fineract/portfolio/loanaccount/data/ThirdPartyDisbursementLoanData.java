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
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

@Getter
@AllArgsConstructor
public class ThirdPartyDisbursementLoanData {

    private final Long loanId;
    private final String loanAccountNo;
    private final String externalId;
    private final LoanStatusEnumData status;
    private final EnumOptionData subStatus;
    private final String thirdPartyDisbursementProvider;
    private final Long loanProductId;
    private final String loanProductName;
    private final BigDecimal approvedPrincipal;
    private final String currencyCode;
    private final LocalDate approvedOnDate;
    private final Long clientId;
    private final String clientName;
    private final String clientExternalId;
}

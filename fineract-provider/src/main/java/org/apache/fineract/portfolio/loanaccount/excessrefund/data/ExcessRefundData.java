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
package org.apache.fineract.portfolio.loanaccount.excessrefund.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExcessRefundData {

    private final Long id;
    private final Long loanId;
    private final String loanAccountNo;
    private final Long clientId;
    private final String clientDisplayName;
    private final Long batchId;
    private final BigDecimal amount;
    private final String currencyCode;
    private final Integer paymentMode;
    private final String paymentModeLabel;
    private final Long paymentTypeId;
    private final Integer status;
    private final String statusLabel;
    private final String beneficiaryJson;
    private final String hubRequestId;
    private final String hubTransactionRef;
    private final String manualPaymentRef;
    private final LocalDate paidOn;
    private final Long submittedById;
    private final String submittedByUsername;
    private final LocalDateTime submittedAt;
    private final Long approvedById;
    private final LocalDateTime approvedAt;
    private final Long postedTransactionId;
    private final String failureReason;
    private final BigDecimal loanTotalOverpaid;
}

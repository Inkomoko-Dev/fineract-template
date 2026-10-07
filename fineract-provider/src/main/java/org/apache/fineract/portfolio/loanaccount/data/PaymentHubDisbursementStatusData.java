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

public final class PaymentHubDisbursementStatusData {

    private final Long loanId;
    private final String requestId;
    private final String status;
    private final String reason;
    private final String transactionRef;
    private final String message;

    public PaymentHubDisbursementStatusData(final Long loanId, final String requestId, final String status, final String reason,
            final String transactionRef, final String message) {
        this.loanId = loanId;
        this.requestId = requestId;
        this.status = status;
        this.reason = reason;
        this.transactionRef = transactionRef;
        this.message = message;
    }

    public Long getLoanId() {
        return this.loanId;
    }

    public String getRequestId() {
        return this.requestId;
    }

    public String getStatus() {
        return this.status;
    }

    public String getReason() {
        return this.reason;
    }

    public String getTransactionRef() {
        return this.transactionRef;
    }

    public String getMessage() {
        return this.message;
    }
}

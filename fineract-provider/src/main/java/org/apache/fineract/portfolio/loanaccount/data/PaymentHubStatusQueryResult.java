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

public final class PaymentHubStatusQueryResult {

    private final int httpStatus;
    private final PaymentHubTransactionStatus transaction;
    private final String errorCode;
    private final String errorMessage;

    private PaymentHubStatusQueryResult(final int httpStatus, final PaymentHubTransactionStatus transaction, final String errorCode,
            final String errorMessage) {
        this.httpStatus = httpStatus;
        this.transaction = transaction;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static PaymentHubStatusQueryResult success(final int httpStatus, final PaymentHubTransactionStatus transaction) {
        return new PaymentHubStatusQueryResult(httpStatus, transaction, null, null);
    }

    public static PaymentHubStatusQueryResult error(final int httpStatus, final String errorCode, final String errorMessage) {
        return new PaymentHubStatusQueryResult(httpStatus, null, errorCode, errorMessage);
    }

    public int getHttpStatus() {
        return this.httpStatus;
    }

    public PaymentHubTransactionStatus getTransaction() {
        return this.transaction;
    }

    public String getErrorCode() {
        return this.errorCode;
    }

    public String getErrorMessage() {
        return this.errorMessage;
    }
}

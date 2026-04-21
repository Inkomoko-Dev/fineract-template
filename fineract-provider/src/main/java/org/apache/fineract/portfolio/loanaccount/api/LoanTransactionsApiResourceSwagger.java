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
package org.apache.fineract.portfolio.loanaccount.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Created by Chirag Gupta on 12/30/17.
 */
final class LoanTransactionsApiResourceSwagger {

    private LoanTransactionsApiResourceSwagger() {}

    @Schema(description = "GetLoansLoanIdTransactionsTemplateResponse")
    public static final class GetLoansLoanIdTransactionsTemplateResponse {

        private GetLoansLoanIdTransactionsTemplateResponse() {}

        static final class GetLoansTransactionType {

            private GetLoansTransactionType() {}

            @Schema(example = "2")
            public Integer id;
            @Schema(example = "loanTransactionType.repayment")
            public String code;
            @Schema(example = "Repayment")
            public String description;
        }

        static final class GetLoansTotal {

            private GetLoansTotal() {}

            @Schema(example = "XOF")
            public String currencyCode;
            @Schema(example = "0")
            public Integer digitsAfterDecimal;
            @Schema(example = "0")
            public Integer inMultiplesOf;
            @Schema(example = "471")
            public Float amount;
            @Schema(example = "CFA Franc BCEAO")
            public String defaultName;
            @Schema(example = "currency.XOF")
            public String nameCode;
            @Schema(example = "CFA")
            public String displaySymbol;
            @Schema(example = "false")
            public Boolean zero;
            @Schema(example = "true")
            public Boolean greaterThanZero;
            @Schema(example = "471 CFA")
            public String displaySymbolValue;
        }

        public GetLoansTransactionType transactionType;
        @Schema(example = "[2009, 8, 1]")
        public LocalDate date;
        @Schema(example = "[2009, 8, 1]", description = "For recoverypayment, the loan write-off date. Clients should not allow transaction dates earlier than this value.")
        public LocalDate writeOffOnDate;
        @Schema(example = "42", description = "Present when retrieving the corrected recovery repost template for a reversed original recovery payment.")
        public Long originalTransactionId;
        @Schema(example = "true")
        public Boolean correctionAllowed;
        @Schema(example = "false", description = "For recovery-payment reversals and reposts, the system now derives any required correction date automatically.")
        public Boolean correctionDateRequired;
        @Schema(example = "[2009, 8, 1]")
        public LocalDate latestClosedAccountingDate;
        @Schema(example = "[2009, 8, 2]")
        public LocalDate earliestCorrectionDate;
        @Schema(example = "[2009, 8, 31]")
        public LocalDate latestCorrectionDate;
        public GetLoansTotal total;
    }

    @Schema(description = "GetLoansLoanIdTransactionsTransactionIdResponse")
    public static final class GetLoansLoanIdTransactionsTransactionIdResponse {

        private GetLoansLoanIdTransactionsTransactionIdResponse() {}

        static final class GetLoansType {

            private GetLoansType() {}

            @Schema(example = "2")
            public Integer id;
            @Schema(example = "loanTransactionType.repayment")
            public String code;
            @Schema(example = "Repayment")
            public String description;
            @Schema(example = "false")
            public Boolean disbursement;
            @Schema(example = "false")
            public Boolean repaymentAtDisbursement;
            @Schema(example = "true")
            public Boolean repayment;
            @Schema(example = "false")
            public Boolean contra;
            @Schema(example = "false")
            public Boolean waiveInterest;
            @Schema(example = "false")
            public Boolean waiveCharges;
            @Schema(example = "false")
            public Boolean writeOff;
            @Schema(example = "false")
            public Boolean recoveryRepayment;
        }

        static final class GetLoansCurrency {

            private GetLoansCurrency() {}

            @Schema(example = "USD")
            public String code;
            @Schema(example = "US Dollar")
            public String name;
            @Schema(example = "2")
            public Integer decimalPlaces;
            @Schema(example = "$")
            public String displaySymbol;
            @Schema(example = "currency.USD")
            public String nameCode;
            @Schema(example = "US Dollar ($)")
            public String displayLabel;
        }

        @Schema(example = "3")
        public Integer id;
        public GetLoansType type;
        @Schema(example = "[2012, 5, 14]")
        public LocalDate date;
        @Schema(example = "[2012, 5, 14]")
        public LocalDate submittedOnDate;
        @Schema(example = "2026-03-31T09:15:00")
        public LocalDateTime createdDate;
        @Schema(example = "mifos")
        public String createdByUsername;
        @Schema(example = "false")
        public Boolean manuallyReversed;
        @Schema(example = "42")
        public Long originalTransactionId;
        @Schema(example = "false")
        public Boolean reversalTransaction;
        @Schema(example = "[2012, 6, 1]")
        public LocalDate correctionDate;
        @Schema(example = "true")
        public Boolean correctionAllowed;
        @Schema(example = "false", description = "For recovery-payment reversals and reposts, the system now derives any required correction date automatically.")
        public Boolean correctionDateRequired;
        @Schema(example = "[2012, 5, 31]")
        public LocalDate latestClosedAccountingDate;
        @Schema(example = "[2012, 6, 1]")
        public LocalDate earliestCorrectionDate;
        @Schema(example = "[2012, 6, 30]")
        public LocalDate latestCorrectionDate;
        public GetLoansCurrency currency;
        @Schema(example = "559.88")
        public Double amount;
        @Schema(example = "559.88")
        public Double interestPortion;
    }

    @Schema(description = "PostLoansLoanIdTransactionsRequest")
    public static final class PostLoansLoanIdTransactionsRequest {

        private PostLoansLoanIdTransactionsRequest() {}

        @Schema(example = "en_GB")
        public String locale;
        @Schema(example = "dd MMMM yyyy")
        public String dateFormat;
        @Schema(example = "28 June 2022", description = "For recoverypayment, the transaction date must be on or after the loan write-off date.")
        public String transactionDate;
        @Schema(example = "50000.00")
        public Double transactionAmount;
        @Schema(example = "An optional note about why your adjusting or changing the transaction.")
        public String note;
        @Schema(example = "3e7791ce-aa10-11ec-b909-0242ac120002")
        public String externalId;
        @Schema(example = "3")
        public Integer paymentTypeId;
        @Schema(example = "42", description = "Optional for reposted recovery payments. Links the corrected transaction back to the original reversed recovery transaction.")
        public Long originalTransactionId;
        @Schema(example = "30 June 2022", description = "Optional override for API clients. If omitted and a reposted recovery payment needs a correction date, the system derives the next open accounting date automatically.")
        public String correctionDate;
    }

    @Schema(description = "PostLoansLoanIdTransactionsResponse")
    public static final class PostLoansLoanIdTransactionsResponse {

        private PostLoansLoanIdTransactionsResponse() {}

        @Schema(example = "1")
        public Integer officeId;
        @Schema(example = "1")
        public Integer clientId;
        @Schema(example = "22")
        public Integer resourceId;
    }

    @Schema(description = "PostLoansLoanIdTransactionsTransactionIdRequest")
    public static final class PostLoansLoanIdTransactionsTransactionIdRequest {

        private PostLoansLoanIdTransactionsTransactionIdRequest() {}

        @Schema(example = "en_GB")
        public String locale;
        @Schema(example = "dd MMMM yyyy")
        public String dateFormat;
        @Schema(example = "28 June 2022")
        public String transactionDate;
        @Schema(example = "50,000.00")
        public Double transactionAmount;
        @Schema(example = "An optional note about why your adjusting or changing the transaction.")
        public String note;
        @Schema(example = "30 June 2022", description = "Optional override for API clients. If omitted and a reversed recovery payment needs a correction date, the system derives the next open accounting date automatically.")
        public String correctionDate;
    }

    @Schema(description = "PostLoansLoanIdTransactionsTransactionIdResponse")
    public static final class PostLoansLoanIdTransactionsTransactionIdResponse {

        private PostLoansLoanIdTransactionsTransactionIdResponse() {}

        @Schema(example = "16")
        public Integer resourceId;
    }

    @Schema(description = "PutChargeTransactionChangesResponse")
    public static final class PutChargeTransactionChangesResponse {

        private PutChargeTransactionChangesResponse() {}

        static final class Changes {

            private Changes() {}

            @Schema(example = "amount")
            public String amount;
        }

        @Schema(example = "1")
        public Integer resourceId;
        @Schema(example = "48")
        public Integer loanId;
        public PutChargeTransactionChangesResponse.Changes changes;

    }

    @Schema(description = "PutChargeTransactionChangesRequest")
    public static final class PutChargeTransactionChangesRequest {

        private PutChargeTransactionChangesRequest() {}

        @Schema(example = "1")
        public Integer id;
        @Schema(example = "2")
        public Integer loanId;
    }
}

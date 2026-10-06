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

public final class ExcessRefundApiConstants {

    private ExcessRefundApiConstants() {}

    public static final String RESOURCE_NAME = "EXCESS_REFUND";
    public static final String BATCH_RESOURCE_NAME = "EXCESS_REFUND_BATCH";

    public static final String LOAN_ID_PARAM = "loanId";
    public static final String AMOUNT_PARAM = "amount";
    public static final String PAYMENT_MODE_PARAM = "paymentMode";
    public static final String PAYMENT_TYPE_ID_PARAM = "paymentTypeId";
    public static final String BENEFICIARY_PARAM = "beneficiary";
    public static final String CHANNEL_PARAM = "channel";
    public static final String ACCOUNT_NUMBER_PARAM = "accountNumber";
    public static final String BANK_CODE_PARAM = "bankCode";
    public static final String BANK_NAME_PARAM = "bankName";
    public static final String MSISDN_PARAM = "msisdn";
    public static final String BENEFICIARY_NAME_PARAM = "beneficiaryName";
    public static final String NOTE_PARAM = "note";
    public static final String PAYMENT_REF_PARAM = "paymentReference";
    public static final String PAID_ON_PARAM = "paidOn";
    public static final String DATE_FORMAT_PARAM = "dateFormat";
    public static final String LOCALE_PARAM = "locale";
    public static final String TRANSACTION_DATE_PARAM = "transactionDate";
    public static final String EXTERNAL_ID_PARAM = "externalId";
    public static final String ITEMS_PARAM = "items";
    public static final String OFFICE_ID_PARAM = "officeId";

    public static final String CHANNEL_BANK = "BANK_TRANSFER";
    public static final String CHANNEL_MOBILE = "MOBILE_MONEY";

    public static final String PAYMENT_MODE_MANUAL = "MANUAL";
    public static final String PAYMENT_MODE_HUB = "PAYMENT_HUB";
}

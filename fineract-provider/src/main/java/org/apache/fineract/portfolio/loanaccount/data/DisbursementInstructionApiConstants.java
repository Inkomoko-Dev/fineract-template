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

public final class DisbursementInstructionApiConstants {

    private DisbursementInstructionApiConstants() {}

    public static final String RESOURCE_NAME = "disbursementInstruction";
    public static final String PERMISSION_CODE = "DISBURSEMENTINSTRUCTION_LOAN";

    public static final String LOAN_ACCOUNT_NO = "loanAccountNo";
    public static final String SOURCE_SYSTEM = "sourceSystem";
    public static final String SUPPLIER_EXTERNAL_ID = "supplierExternalId";
    public static final String ACTUAL_DISBURSEMENT_DATE = "actualDisbursementDate";
    public static final String LOCALE = "locale";
    public static final String DATE_FORMAT = "dateFormat";

    public static final String LOAN_ID = "loanId";
    public static final String SUPPLIER_ID = "supplierId";
    public static final String DISBURSEMENT_REQUEST_STATUS = "disbursementRequestStatus";
    public static final String SUCCESS = "success";
    public static final String INSTRUCTION_ID = "instructionId";
    public static final String INSTRUCTION_STATUS = "instructionStatus";
    public static final String IDEMPOTENCY_KEY = "idempotencyKey";
}

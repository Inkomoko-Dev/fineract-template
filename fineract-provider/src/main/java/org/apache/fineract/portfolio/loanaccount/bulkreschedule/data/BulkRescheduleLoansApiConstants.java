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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.data;

import org.apache.fineract.portfolio.loanaccount.rescheduleloan.RescheduleLoansApiConstants;

public class BulkRescheduleLoansApiConstants extends RescheduleLoansApiConstants{



    private BulkRescheduleLoansApiConstants() {
        super();
    }

    public static final String ENTITY_NAME = "BULKRESCHEDULELOAN";
    public static final String FILTERS_PARAM_NAME = "filters";
    public static final String RESCHEDULE_DETAIL_PARAM_NAME = "reschedulingDetails";
    public static final String DRY_RUN_PARAM_NAME = "dryRun";
    public static final String ROLLBACK_REASON_PARAM_NAME = "rollbackReason";

    public static final String OFFICE_PARAM_NAME = "officeId";
    public static final String LOAN_STATUSES_PARAM_NAME = "loanStatus";
    public static final String LOAN_PRODUCTS_PARAM_NAME = "loanProductIds";
    public static final String LOAN_OFFICERS_PARAM_NAME = "loanOfficerIds";
    public static final String RESCHEDULE_FROM_DATE_STRATEGY_PARAM_NAME = "rescheduleFromDateStrategy";
    public static final String CURRENT_INTEREST_RATE_PARAM_NAME = "currentInterestRate";
    public static final String EXCLUDED_LOAN_IDS_PARAM_NAME = "excludedLoanIds";

    public static final String EXECUTION_ID_PARAM_NAME = "executionId";
    public static final String APPROVAL_NOTE_PARAM_NAME = "approvalNote";



}

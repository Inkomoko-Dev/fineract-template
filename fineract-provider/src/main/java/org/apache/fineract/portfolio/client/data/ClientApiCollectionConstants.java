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
package org.apache.fineract.portfolio.client.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;

public class ClientApiCollectionConstants extends ClientApiConstants {

    protected static final Set<String> CLIENT_CREATE_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(familyMembers, address, localeParamName, dateFormatParamName, groupIdParamName, accountNoParamName,
                    externalIdParamName, mobileNoParamName, emailAddressParamName, firstnameParamName, middlenameParamName,
                    lastnameParamName, fullnameParamName, officeIdParamName, activeParamName, activationDateParamName, staffIdParamName,
                    submittedOnDateParamName, savingsProductIdParamName, dateOfBirthParamName, genderIdParamName, clientTypeIdParamName,
                    clientClassificationIdParamName, clientLevelIdParamName, clientNonPersonDetailsParamName, displaynameParamName,
                    legalFormIdParamName, datatables, isStaffParamName, businessOwners, dailyWithdrawLimit, singleWithdrawLimit,
                    maritalStatusIdParamName, titleParam, mnemonicsParamNameParam, altMobileNoParam, initialsParam, isRegisteredParam,
                    inBusinessSinceParamName, KIVA_ID, PHYSICAL_ADDRESS_DISTRICT, PHYSICAL_ADDRESS_CELL, PHYSICAL_ADDRESS_SECTOR,
                    migratedParamName));

    protected static final Set<String> CLIENT_NON_PERSON_CREATE_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(familyMembers, address, localeParamName, dateFormatParamName, incorpNumberParamName, remarksParamName,
                    incorpValidityTillParamName, constitutionIdParamName, mainBusinessLineIdParamName, datatables, mnemonicsParamNameParam,
                    altMobileNoParam, inBusinessSinceParamName, isRegisteredParam, KIVA_ID, PHYSICAL_ADDRESS_DISTRICT,
                    PHYSICAL_ADDRESS_CELL, PHYSICAL_ADDRESS_SECTOR));

    protected static final Set<String> CLIENT_UPDATE_REQUEST_DATA_PARAMETERS = new HashSet<>(Arrays.asList(localeParamName,
            dateFormatParamName, accountNoParamName, externalIdParamName, mobileNoParamName, emailAddressParamName, firstnameParamName,
            middlenameParamName, clientLevelIdParamName, lastnameParamName, fullnameParamName, activeParamName, activationDateParamName,
            staffIdParamName, savingsProductIdParamName, dateOfBirthParamName, genderIdParamName, clientTypeIdParamName,
            clientClassificationIdParamName, submittedOnDateParamName, clientNonPersonDetailsParamName, displaynameParamName,
            legalFormIdParamName, isStaffParamName, dailyWithdrawLimit, singleWithdrawLimit, maritalStatusIdParamName, titleParam,
            mnemonicsParamNameParam, altMobileNoParam, initialsParam, isRegisteredParam, inBusinessSinceParamName, KIVA_ID,
            PHYSICAL_ADDRESS_DISTRICT, PHYSICAL_ADDRESS_CELL, PHYSICAL_ADDRESS_SECTOR));

    protected static final Set<String> CLIENT_NON_PERSON_UPDATE_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, incorpNumberParamName, remarksParamName, incorpValidityTillParamName,
                    constitutionIdParamName, mainBusinessLineIdParamName, altMobileNoParam, isRegisteredParam, inBusinessSinceParamName,
                    KIVA_ID, PHYSICAL_ADDRESS_DISTRICT, PHYSICAL_ADDRESS_CELL, PHYSICAL_ADDRESS_SECTOR));

    /**
     * These parameters will match the class level parameters of {@link ClientData}. Where possible, we try to get
     * response parameters to match those of request parameters.
     */

    protected static final Set<String> ACTIVATION_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, activationDateParamName));
    protected static final Set<String> REACTIVATION_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, reactivationDateParamName));

    protected static final Set<String> CLIENT_CLOSE_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, closureDateParamName, closureReasonIdParamName));

    protected static final Set<String> CLIENT_REJECT_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, rejectionDateParamName, rejectionReasonIdParamName));

    protected static final Set<String> CLIENT_WITHDRAW_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, withdrawalDateParamName, withdrawalReasonIdParamName));

    protected static final Set<String> UNDOREJECTION_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, reopenedDateParamName));

    protected static final Set<String> UNDOWITHDRAWN_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(localeParamName, dateFormatParamName, reopenedDateParamName));

    protected static final Set<String> CLIENT_CHARGES_ADD_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(chargeIdParamName, amountParamName, dueAsOfDateParamName, dateFormatParamName, localeParamName));

    protected static final Set<String> CLIENT_CHARGES_PAY_CHARGE_REQUEST_DATA_PARAMETERS = new HashSet<>(Arrays.asList(amountParamName,
            transactionDateParamName, dateFormatParamName, localeParamName, paymentTypeIdParamName, transactionAccountNumberParamName,
            checkNumberParamName, routingCodeParamName, receiptNumberParamName, bankNumberParamName));
    protected static final Set<String> CLIENT_BUSINESS_DETAIL_CREATE_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(CLIENT_ID, BUSINESS_TYPE, BUSINESS_CREATION_DATE, STARTING_CAPITAL, SOURCE_OF_CAPITAL, TOTAL_EMPLOYEE,
                    BUSINESS_REVENUE, AVERAGE_MONTHLY_REVENUE, BEST_MONTH, REASON_FOR_BEST_MONTH, WORST_MONTH, REASON_FOR_WORST_MONTH,
                    NUMBER_OF_PURCHASE, PURCHASE_FREQUENCY, TOTAL_PURCHASE_LAST_MONTH, WHEN_LAST_PURCHASE, LAST_PURCHASE_AMOUNT,
                    BUSINESS_ASSET_AMOUNT, AMOUNT_AT_CASH, AMOUNT_AT_SAVING, AMOUNT_AT_INVENTORY, FIXED_ASSET_COST, TOTAL_IN_TAX,
                    TOTAL_IN_TRANSPORT, TOTAL_IN_RENT, TOTAL_IN_COMMUNICATION, OTHER_EXPENSE, OTHER_EXPENSE_AMOUNT, TOTAL_UTILITY,
                    TOTAL_WORKER_SALARY, TOTAL_WAGE, EXTERNAL_ID, SOCIETY, localeParamName, dateFormatParamName));

    protected static final Set<String> CLIENT_BUSINESS_DETAIL_UPDATE_REQUEST_DATA_PARAMETERS = new HashSet<>(
            Arrays.asList(ID, CLIENT_ID, BUSINESS_TYPE, BUSINESS_CREATION_DATE, STARTING_CAPITAL, SOURCE_OF_CAPITAL, TOTAL_EMPLOYEE,
                    BUSINESS_REVENUE, AVERAGE_MONTHLY_REVENUE, BEST_MONTH, REASON_FOR_BEST_MONTH, WORST_MONTH, REASON_FOR_WORST_MONTH,
                    NUMBER_OF_PURCHASE, PURCHASE_FREQUENCY, TOTAL_PURCHASE_LAST_MONTH, WHEN_LAST_PURCHASE, LAST_PURCHASE_AMOUNT,
                    BUSINESS_ASSET_AMOUNT, AMOUNT_AT_CASH, AMOUNT_AT_SAVING, AMOUNT_AT_INVENTORY, FIXED_ASSET_COST, TOTAL_IN_TAX,
                    TOTAL_IN_TRANSPORT, TOTAL_IN_RENT, TOTAL_IN_COMMUNICATION, OTHER_EXPENSE, OTHER_EXPENSE_AMOUNT, TOTAL_UTILITY,
                    TOTAL_WORKER_SALARY, TOTAL_WAGE, EXTERNAL_ID, SOCIETY, localeParamName, dateFormatParamName));

}

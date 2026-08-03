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
package org.apache.fineract.portfolio.client.api;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.fineract.portfolio.client.data.ClientData;

@SuppressWarnings({ "HideUtilityClassConstructor" })
public class ClientApiConstants {

    public static final String CLIENT_RESOURCE_NAME = "client";
    public static final String CLIENT_BUSINESS_DETAIL_RESOURCE_NAME = "businessDetail";

    public static final String CLIENT_LEVELS = "clientLevels";
    public static final String CLIENT_CHARGES_RESOURCE_NAME = "CLIENTCHARGE";

    // Client Charge Action Names
    public static final String CLIENT_CHARGE_ACTION_CREATE = "CREATE";
    public static final String CLIENT_CHARGE_ACTION_DELETE = "DELETE";
    public static final String CLIENT_CHARGE_ACTION_WAIVE = "WAIVE";
    public static final String CLIENT_CHARGE_ACTION_PAY = "PAY";
    public static final String CLIENT_CHARGE_ACTION_INACTIVATE = "INACTIVATE";

    // Client charge associations and query parameters
    public static final String CLIENT_CHARGE_QUERY_PARAM_STATUS = "chargeStatus";
    public static final String CLIENT_CHARGE_QUERY_PARAM_STATUS_VALUE_ALL = "all";
    public static final String CLIENT_CHARGE_QUERY_PARAM_STATUS_VALUE_ACTIVE = "active";
    public static final String CLIENT_CHARGE_QUERY_PARAM_STATUS_VALUE_INACTIVE = "inactive";
    public static final String CLIENT_CHARGE_ASSOCIATIONS_TRANSACTIONS = "transactions";

    // Client transaction action names
    public static final String CLIENT_TRANSACTION_ACTION_READ = "READTRANSACTION";
    public static final String CLIENT_TRANSACTION_ACTION_UNDO = "UNDOTRANSACTION";

    // Commands
    public static final String CLIENT_CHARGE_COMMAND_WAIVE_CHARGE = "waive";
    public static final String CLIENT_CHARGE_COMMAND_PAY_CHARGE = "paycharge";
    public static final String CLIENT_CHARGE_COMMAND_INACTIVATE_CHARGE = "inactivate";
    public static final String CLIENT_TRANSACTION_COMMAND_UNDO = "undo";

    public static final String CLIENT_CLOSURE_REASON = "ClientClosureReason";
    public static final String CLIENT_ACTION_REASON = "ClientActionReason";
    public static final String CLIENT_REJECT_REASON = "ClientRejectReason";
    public static final String CLIENT_WITHDRAW_REASON = "ClientWithdrawReason";

    public static final String GENDER = "Gender";
    public static final String CLIENT_TYPE = "ClientType";
    public static final String CLIENT_CLASSIFICATION = "ClientClassification";

    public static final String CLIENT_NON_PERSON_CONSTITUTION = "Constitution";
    public static final String CLIENT_NON_PERSON_MAIN_BUSINESS_LINE = "Main Business Line";

    public static final String TITLE = "TITLE";

    public static final String PRODUCT_CATEGORY = "ProductCategory";

    public static final String PRODUCT_TYPE = "ProductType";

    // general
    public static final String localeParamName = "locale";
    public static final String dateFormatParamName = "dateFormat";
    public static final String address = "address";
    public static final String familyMembers = "familyMembers";
    public static final String businessOwners = "businessOwners";
    public static final String MARITALSTATUS = "MARITAL STATUS";
    public static final String maritalStatusIdParamName = "maritalStatusId";

    // request parameters
    public static final String idParamName = "id";
    public static final String groupIdParamName = "groupId";
    public static final String accountNoParamName = "accountNo";
    public static final String externalIdParamName = "externalId";
    public static final String kivaIdParamName = "kivaId";
    public static final String mobileNoParamName = "mobileNo";
    public static final String emailAddressParamName = "emailAddress";
    public static final String firstnameParamName = "firstname";
    public static final String middlenameParamName = "middlename";
    public static final String lastnameParamName = "lastname";
    public static final String fullnameParamName = "fullname";
    public static final String displaynameParamName = "displayname";
    public static final String officeIdParamName = "officeId";
    public static final String transferOfficeIdParamName = "transferOfficeIdParamName";
    public static final String activeParamName = "active";
    public static final String activationDateParamName = "activationDate";
    public static final String reactivationDateParamName = "reactivationDate";
    public static final String staffIdParamName = "staffId";
    public static final String isStaffParamName = "isStaff";
    public static final String closureDateParamName = "closureDate";
    public static final String closureReasonIdParamName = "closureReasonId";
    public static final String reopenedDateParamName = "reopenedDate";

    public static final String rejectionDateParamName = "rejectionDate";
    public static final String rejectionReasonIdParamName = "rejectionReasonId";
    public static final String withdrawalDateParamName = "withdrawalDate";
    public static final String withdrawalReasonIdParamName = "withdrawalReasonId";

    public static final String submittedOnDateParamName = "submittedOnDate";
    public static final String yearArrivedInHostCountryParamName = "yearArrivedInHostCountry";
    public static final String savingsProductIdParamName = "savingsProductId";
    public static final String savingsAccountIdParamName = "savingsAccountId";
    public static final String dateOfBirthParamName = "dateOfBirth";
    public static final String genderIdParamName = "genderId";
    public static final String genderParamName = "gender";
    public static final String clientTypeIdParamName = "clientTypeId";
    public static final String clientTypeParamName = "clientType";
    public static final String clientClassificationIdParamName = "clientClassificationId";
    public static final String clientClassificationParamName = "clientClassification";
    public static final String legalFormIdParamName = "legalFormId";
    public static final String legalFormParamName = "legalForm";
    // request parameters for payment details
    public static final String paymentTypeIdParamName = "paymentTypeId";
    public static final String transactionAccountNumberParamName = "accountNumber";
    public static final String checkNumberParamName = "checkNumber";
    public static final String routingCodeParamName = "routingCode";
    public static final String receiptNumberParamName = "receiptNumber";
    public static final String bankNumberParamName = "bankNumber";

    // request parameters for client non person
    public static final String clientNonPersonDetailsParamName = "clientNonPersonDetails";
    public static final String incorpNumberParamName = "incorpNumber";
    public static final String remarksParamName = "remarks";
    public static final String incorpValidityTillParamName = "incorpValidityTillDate";
    public static final String constitutionIdParamName = "constitutionId";
    public static final String mainBusinessLineIdParamName = "mainBusinessLineId";

    // response parameters
    public static final String statusParamName = "status";
    public static final String hierarchyParamName = "hierarchy";
    public static final String displayNameParamName = "displayName";
    public static final String officeNameParamName = "officeName";
    public static final String staffNameParamName = "staffName";
    public static final String trasnferOfficeNameParamName = "transferOfficeName";
    public static final String transferToOfficeNameParamName = "transferToOfficeName";
    public static final String transferToOfficeIdParamName = "transferToOfficeId";
    public static final String imageKeyParamName = "imageKey";
    public static final String imageIdParamName = "imageId";
    public static final String imagePresentParamName = "imagePresent";
    public static final String timelineParamName = "timeline";

    // client charges response parameters
    public static final String chargeIdParamName = "chargeId";
    public static final String clientIdParamName = "clientId";
    public static final String chargesParamName = "charges";
    public static final String chargeNameParamName = "name";
    public static final String penaltyParamName = "penalty";
    public static final String chargeTimeTypeParamName = "chargeTimeType";
    public static final String dueAsOfDateParamName = "dueDate";
    public static final String transactionDateParamName = "transactionDate";
    public static final String chargeCalculationTypeParamName = "chargeCalculationType";
    public static final String currencyParamName = "currency";
    public static final String amountWaivedParamName = "amountWaived";
    public static final String amountWrittenOffParamName = "amountWrittenOff";
    public static final String amountOutstandingParamName = "amountOutstanding";
    public static final String amountOrPercentageParamName = "amountOrPercentage";
    public static final String amountParamName = "amount";
    public static final String amountPaidParamName = "amountPaid";
    public static final String chargeOptionsParamName = "chargeOptions";
    public static final String transactionsParamName = "transactions";

    // client transactions response parameters
    public static final String transactionAmountParamName = "transactionAmount";
    public static final String paymentDetailDataParamName = "paymentDetailData";
    public static final String reversedParamName = "reversed";
    public static final String dateParamName = "date";
    private static final String transactionTypeParamName = "type";
    private static final String transactionCurrencyParamName = "currency";

    // associations related part of response
    public static final String groupsParamName = "groups";

    // template related part of response
    public static final String officeOptionsParamName = "officeOptions";
    public static final String staffOptionsParamName = "staffOptions";

    public static final String datatables = "datatables";
    public static final String obligeeData = "ObligeeDetails";

    public static final String clientEntityName = "clients";
    public static final String clientLevelIdParamName = "clientLevelId";

    public static final String singleWithdrawLimit = "singleWithdrawLimit";
    public static final String dailyWithdrawLimit = "dailyWithdrawLimit";

    public static final String initialsParam = "initials";
    public static final String mnemonicsParamNameParam = "mnemonics";
    public static final String altMobileNoParam = "altMobileNo";
    public static final String titleParam = "titleId";

    public static final String inBusinessSinceParamName = "inBusinessSince";
    public static final String KIVA_ID = "kivaId";
    public static final String PHYSICAL_ADDRESS_DISTRICT = "physicalAddressDistrict";
    public static final String PHYSICAL_ADDRESS_SECTOR = "physicalAddressSector";
    public static final String PHYSICAL_ADDRESS_CELL = "physicalAddressCell";

    public static final String isRegisteredParam = "isRegistered";

    public static final String atAddressSinceParamName = "atAddressSince";

    private static final String createdDate = "createdDate";

    private static final String clientAdditionalInfoData = "clientAdditionalInfoData";

    public static final String otherExpensesAmountParamName = "otherExpensesAmount";

    public static final String otherExpensesIdParamName = "otherExpensesId";

    public static final String otherExpensesListParamName = "otherExpensesList";

    public static final String utilitiesAmountParamName = "utilitiesAmount";
    public static final String rentAmountParamName = "rentAmount";
    public static final String schoolFessAmountParamName = "schoolFessAmount";

    public static final String foodExpensesAmountParamName = "foodExpensesAmount";

    public static final String CLIENT_HOUSEHOLD_EXPENSES_RESOURCE_NAME = "HouseholdExpenses";

    public static final String OTHER_HOUSEHOLD_EXPENSES = "OtherExpenses";

    // other info
    public static final String CLIENT_OTHER_INFO_RESOURCE_NAME = "ClientOtherInfo";
    public static final String numberOfChildren = "numberOfChildren";
    public static final String numberOfDependents = "numberOfDependents";
    public static final String yearArrivedInHostCountry = "yearArrivedInHostCountryId";
    public static final String guarantor = "guarantor";
    public static final String strataIdParamName = "strataId";
    public static final String nationalityIdParamName = "nationalityId";
    public static final String NATIONAL_IDENTIFICATION_NUMBER = "nationalIdentificationNumber";
    public static final String BANK_ACCOUNT_NUMBER = "bankAccountNumber";
    public static final String BANK_NAME = "bankName";
    public static final String BANK_ID = "bankId";
    public static final String PASSPORT_NUMBER = "passportNumber";
    public static final String STRATA = "Strata";
    public static final String YEAR_ARRIVED_IN_HOST_COUNTRY = "YearArrivedInHostCountry";
    public static final String coSignors = "coSignorsName";
    public static String businessLocationParamName = "businessLocation";
    public static String taxIdentificationNumberParamName = "taxIdentificationNumber";
    public static String incomeGeneratingActivityParamName = "incomeGeneratingActivity";
    public static String incomeGeneratingActivityMonthlyAmountParamName = "incomeGeneratingActivityMonthlyAmount";
    public static String telephoneNoParamName = "telephoneNo";

    /**
     * These parameters will match the class level parameters of {@link ClientData}. Where possible, we try to get
     * response parameters to match those of request parameters.
     */
    protected static final Set<String> CLIENT_RESPONSE_DATA_PARAMETERS = new HashSet<>(Arrays.asList(idParamName, accountNoParamName,
            externalIdParamName, statusParamName, activeParamName, activationDateParamName, firstnameParamName, middlenameParamName,
            lastnameParamName, fullnameParamName, displayNameParamName, mobileNoParamName, emailAddressParamName, officeIdParamName,
            officeNameParamName, transferToOfficeIdParamName, transferToOfficeNameParamName, hierarchyParamName, imageIdParamName,
            imagePresentParamName, staffIdParamName, staffNameParamName, timelineParamName, groupsParamName, officeOptionsParamName,
            staffOptionsParamName, dateOfBirthParamName, genderParamName, clientTypeParamName, clientClassificationParamName,
            legalFormParamName, clientNonPersonDetailsParamName, isStaffParamName, clientLevelIdParamName, dailyWithdrawLimit,
            singleWithdrawLimit, initialsParam, maritalStatusIdParamName, mnemonicsParamNameParam, altMobileNoParam, createdDate,
            clientAdditionalInfoData, "clientBusinessDetailData"));

    protected static final Set<String> CLIENT_CHARGES_RESPONSE_DATA_PARAMETERS = new HashSet<>(Arrays.asList(chargeIdParamName,
            clientIdParamName, chargeNameParamName, penaltyParamName, chargeTimeTypeParamName, dueAsOfDateParamName,
            chargeCalculationTypeParamName, currencyParamName, amountWaivedParamName, amountWrittenOffParamName, amountOutstandingParamName,
            amountOrPercentageParamName, amountParamName, amountPaidParamName, chargeOptionsParamName, transactionsParamName));

    protected static final Set<String> CLIENT_TRANSACTION_RESPONSE_DATA_PARAMETERS = new HashSet<>(Arrays.asList(idParamName,
            transactionAmountParamName, paymentDetailDataParamName, reversedParamName, dateParamName, officeIdParamName,
            officeNameParamName, transactionTypeParamName, transactionCurrencyParamName, externalIdParamName, submittedOnDateParamName));

    public static final Set<String> CLIENT_HOUSEHOLD_EXPENSES_DATA_PARAMETERS = new HashSet<>(Arrays.asList(foodExpensesAmountParamName,
            schoolFessAmountParamName, rentAmountParamName, utilitiesAmountParamName, otherExpensesListParamName));

    // Client Business Detail Param
    public static final String BUSINESS_TYPE = "businessType";
    public static final String BUSINESS_TYPE_CODE = "BusinessType";
    public static final String BUSINESS_CREATION_DATE = "businessCreationDate";
    public static final String STARTING_CAPITAL = "startingCapital";
    public static final String SOURCE_OF_CAPITAL = "sourceOfCapital";
    public static final String SOURCE_OF_CAPITAL_CODE = "SourceOfCapital";
    public static final String TOTAL_EMPLOYEE = "totalEmployee";
    public static final String BUSINESS_REVENUE = "businessRevenue";
    public static final String AVERAGE_MONTHLY_REVENUE = "averageMonthlyRevenue";
    public static final String BEST_MONTH = "bestMonth";
    public static final String REASON_FOR_BEST_MONTH = "reasonForBestMonth";
    public static final String WORST_MONTH = "worstMonth";
    public static final String REASON_FOR_WORST_MONTH = "reasonForWorstMonth";
    public static final String NUMBER_OF_PURCHASE = "numberOfPurchase";
    public static final String PURCHASE_FREQUENCY = "purchaseFrequency";
    public static final String TOTAL_PURCHASE_LAST_MONTH = "totalPurchaseLastMonth";
    public static final String WHEN_LAST_PURCHASE = "whenLastPurchase";
    public static final String LAST_PURCHASE_AMOUNT = "lastPurchaseAmount";
    public static final String BUSINESS_ASSET_AMOUNT = "businessAssetAmount";
    public static final String AMOUNT_AT_CASH = "amountAtCash";
    public static final String AMOUNT_AT_SAVING = "amountAtSaving";
    public static final String AMOUNT_AT_INVENTORY = "amountAtInventory";
    public static final String FIXED_ASSET_COST = "fixedAssetCost";
    public static final String TOTAL_IN_TAX = "totalInTax";
    public static final String TOTAL_IN_TRANSPORT = "totalInTransport";
    public static final String TOTAL_IN_RENT = "totalInRent";
    public static final String TOTAL_IN_COMMUNICATION = "totalInCommunication";
    public static final String OTHER_EXPENSE = "otherExpense";
    public static final String OTHER_EXPENSE_AMOUNT = "otherExpenseAmount";
    public static final String TOTAL_UTILITY = "totalUtility";
    public static final String TOTAL_WORKER_SALARY = "totalWorkerSalary";
    public static final String TOTAL_WAGE = "totalWage";
    public static final String SOCIETY = "society";
    public static final String EXTERNAL_ID = "externalId";
    public static final String CLIENT_ID = "clientId";
    public static final String ID = "id";
    public static final String READ_CLIENTBUSINESSDETAIL = "READ_CLIENTBUSINESSDETAIL";

    protected static final Set<String> CLIENT_BUSINESS_DETAIL_RESPONSE_REQUEST_DATA_PARAMETERS = new HashSet<>(Arrays.asList(CLIENT_ID,
            BUSINESS_TYPE, BUSINESS_CREATION_DATE, STARTING_CAPITAL, SOURCE_OF_CAPITAL, TOTAL_EMPLOYEE, BUSINESS_REVENUE,
            AVERAGE_MONTHLY_REVENUE, BEST_MONTH, REASON_FOR_BEST_MONTH, WORST_MONTH, REASON_FOR_WORST_MONTH, NUMBER_OF_PURCHASE,
            PURCHASE_FREQUENCY, TOTAL_PURCHASE_LAST_MONTH, WHEN_LAST_PURCHASE, LAST_PURCHASE_AMOUNT, BUSINESS_ASSET_AMOUNT, AMOUNT_AT_CASH,
            AMOUNT_AT_SAVING, AMOUNT_AT_INVENTORY, FIXED_ASSET_COST, TOTAL_IN_TAX, TOTAL_IN_TRANSPORT, TOTAL_IN_RENT,
            TOTAL_IN_COMMUNICATION, OTHER_EXPENSE, OTHER_EXPENSE_AMOUNT, TOTAL_UTILITY, TOTAL_WORKER_SALARY, TOTAL_WAGE, EXTERNAL_ID));
    public static final String BUSINESS_TYPE_OPTIONS = "BusinessType";
    public static final String SOURCE_OF_CAPITAL_OPTIONS = "SourceOfCapital";

    // Client Recruitment survey
    public static final String COUNTRY = "COUNTRY";
    public static final String COHORT = "Cohort";
    public static final String PROGRAM = "Program";
    public static final String SURVEY_LOCATION = "SurveyLocation";

    public static final String surveyNameParamName = "surveyName";
    public static final String surveyLocationParamName = "surveyLocation";
    public static final String startDateParamName = "startDate";
    public static final String endDateParamName = "endDate";
    public static final String countryIdParamName = "countryId";
    public static final String cohortIdParamName = "cohortId";
    public static final String programIdParamName = "programId";
    public static final String surveyLocationIdParamName = "surveyLocationId";

    public static final String CLIENT_RECRUITMENT_SURVEY_RESOURCE_NAME = "ClientRecruitmentSurvey";

    public static final Set<String> CLIENT_RECRUITMENT_SURVEY_RESPONSE_REQUEST_PARAMETER = new HashSet<>(
            Arrays.asList(surveyNameParamName, surveyLocationParamName, startDateParamName, endDateParamName, countryIdParamName,
                    cohortIdParamName, programIdParamName, localeParamName, dateFormatParamName, surveyLocationIdParamName));

    public static final String upiNoParamName = "upiNo";
    public static final String chassisNoParamName = "chassisNo";
    public static final String collateralOwnerFirstParamName = "collateralOwnerFirst";
    public static final String collateralOwnerSecondParamName = "collateralOwnerSecond";
    public static final String idNoOfCollateralOwnerFirstParamName = "idNoOfCollateralOwnerFirst";
    public static final String idNoOfCollateralOwnerSecondParamName = "idNoOfCollateralOwnerSecond";
    public static final String provinceIdParamName = "provinceId";
    public static final String districtIdParamName = "districtId";
    public static final String sectorIdParamName = "sectorId";
    public static final String cellIdParamName = "cellId";
    public static final String villageIdParamName = "villageId";

    public static final String worthOfCollateralParamName = "worthOfCollateral";

    public static final String PROVINCE = "Province";
    public static final String DISTRICT = "District";
    public static final String SECTOR = "Sector";
    public static final String CELL = "Cell";
    public static final String VILLAGE = "Village";
    public static final String CLIENT_COLLATERAL_ADDITIONAL_DATA_RESOURCE_NAME = "ClientCollateralAdditionalData";
    public static final Set<String> CLIENT_COLLATERAL_ADDITIONAL_DATA_RESPONSE_REQUEST_PARAMETER = new HashSet<>(Arrays.asList(
            upiNoParamName, chassisNoParamName, collateralOwnerFirstParamName, collateralOwnerSecondParamName,
            idNoOfCollateralOwnerFirstParamName, idNoOfCollateralOwnerSecondParamName, worthOfCollateralParamName, provinceIdParamName,
            districtIdParamName, sectorIdParamName, cellIdParamName, villageIdParamName, localeParamName, "quantity", "collateralId"));

}

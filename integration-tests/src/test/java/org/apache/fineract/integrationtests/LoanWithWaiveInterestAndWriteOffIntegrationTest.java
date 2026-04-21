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
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.google.gson.Gson;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.apache.fineract.client.models.GlobalConfigurationPropertyData;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client Loan Integration Test for checking Loan Disbursement with Waive Interest and Write-Off.
 */
@SuppressWarnings({ "rawtypes" })
public class LoanWithWaiveInterestAndWriteOffIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(LoanWithWaiveInterestAndWriteOffIntegrationTest.class);
    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;

    private static final String LP_PRINCIPAL = "12,000.00";
    private static final String LP_REPAYMENTS = "2";
    private static final String LP_REPAYMENT_PERIOD = "6";
    private static final String LP_INTEREST_RATE = "1";
    private static final String PRINCIPAL = "4,500.00";
    private static final String LOAN_TERM_FREQUENCY = "18";
    private static final String NUMBER_OF_REPAYMENTS = "9";
    private static final String REPAYMENT_PERIOD = "2";
    private static final String DISBURSEMENT_DATE = "30 October 2010";
    private static final String LOAN_APPLICATION_SUBMISSION_DATE = "23 September 2010";
    private static final String EXPECTED_DISBURSAL_DATE = "28 October 2010";
    private static final String RATE_OF_INTEREST_PER_PERIOD = "2";
    private static final String DATE_OF_JOINING = "04 March 2009";
    private static final String INTEREST_VALUE_AMOUNT = "40.00";
    private static final String RECOVERY_PAYMENT = "recoverypayment";
    private static final String RECOVERY_PAYMENT_BEFORE_WRITEOFF_ERROR =
            "error.msg.loan.recovery.payment.date.cannot.be.before.writeoff.date";
    private static final String DUPLICATE_CORRECTED_RECOVERY_ERROR =
            "error.msg.loan.recovery.payment.correction.already.exists";
    private LoanTransactionHelper loanTransactionHelper;
    private LoanTransactionHelper loanTransactionHelperValidationError;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, this.responseSpec);
        this.loanTransactionHelperValidationError = new LoanTransactionHelper(this.requestSpec, new ResponseSpecBuilder().build());
    }

    @Test
    public void checkClientLoanCreateAndDisburseFlow() {
        // CREATE CLIENT
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec, DATE_OF_JOINING);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        // CREATE LOAN PRODUCT
        final Integer loanProductID = createLoanProduct();
        // APPLY FOR LOAN
        final Integer loanID = applyForLoanApplication(clientID, loanProductID);

        HashMap loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(this.requestSpec, this.responseSpec, loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);

        LOG.info("-----------------------------------APPROVE LOAN-----------------------------------------");
        loanStatusHashMap = this.loanTransactionHelper.approveLoan("28 September 2010", loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);
        LoanStatusChecker.verifyLoanIsWaitingForDisbursal(loanStatusHashMap);

        // UNDO APPROVAL
        loanStatusHashMap = this.loanTransactionHelper.undoApproval(loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);

        LOG.info("-----------------------------------RE-APPROVE LOAN-----------------------------------------");
        loanStatusHashMap = this.loanTransactionHelper.approveLoan("01 October 2010", loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);
        LoanStatusChecker.verifyLoanIsWaitingForDisbursal(loanStatusHashMap);

        // DISBURSE
        String loanDetails = this.loanTransactionHelper.getLoanDetails(this.requestSpec, this.responseSpec, loanID);
        loanStatusHashMap = this.loanTransactionHelper.disburseLoan(DISBURSEMENT_DATE, loanID,
                JsonPath.from(loanDetails).get("netDisbursalAmount").toString());
        LOG.info("DISBURSE {}", loanStatusHashMap.toString());
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);

        // PERFORM REPAYMENTS AND CHECK LOAN STATUS
        this.loanTransactionHelper.verifyRepaymentScheduleEntryFor(1, 4000.0F, loanID);
        this.loanTransactionHelper.makeRepayment("01 January 2011", 540.0f, loanID);

        // UNDO DISBURSE LOAN
        loanStatusHashMap = this.loanTransactionHelper.undoDisbursal(loanID);
        LoanStatusChecker.verifyLoanIsWaitingForDisbursal(loanStatusHashMap);

        // DIBURSE AGAIN
        loanStatusHashMap = this.loanTransactionHelper.disburseLoan(DISBURSEMENT_DATE, loanID,
                JsonPath.from(loanDetails).get("netDisbursalAmount").toString());
        LOG.info("DISBURSE {}", loanStatusHashMap);
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);

        // MAKE REPAYMENTS
        final float repayment_with_interest = 540.0f;
        final float repayment_without_interest = 500.0f;

        this.loanTransactionHelper.verifyRepaymentScheduleEntryFor(1, 4000.0F, loanID);
        this.loanTransactionHelper.makeRepayment("01 January 2011", repayment_with_interest, loanID);
        this.loanTransactionHelper.makeRepayment("01 March 2011", repayment_with_interest, loanID);
        this.loanTransactionHelper.waiveInterest("01 May 2011", INTEREST_VALUE_AMOUNT, loanID);
        this.loanTransactionHelper.makeRepayment("01 May 2011", repayment_without_interest, loanID);
        this.loanTransactionHelper.makeRepayment("01 July 2011", repayment_with_interest, loanID);
        this.loanTransactionHelper.waiveInterest("01 September 2011", INTEREST_VALUE_AMOUNT, loanID);
        this.loanTransactionHelper.makeRepayment("01 September 2011", repayment_without_interest, loanID);
        this.loanTransactionHelper.makeRepayment("01 November 2011", repayment_with_interest, loanID);
        this.loanTransactionHelper.waiveInterest("01 January 2012", INTEREST_VALUE_AMOUNT, loanID);
        this.loanTransactionHelper.makeRepayment("01 January 2012", repayment_without_interest, loanID);
        this.loanTransactionHelper.verifyRepaymentScheduleEntryFor(7, 1000.0f, loanID);

        // WRITE OFF LOAN AND CHECK ACCOUNT IS CLOSED
        LoanStatusChecker.verifyLoanAccountIsClosed(this.loanTransactionHelper.writeOffLoan("01 March 2012", loanID));

    }

    @Test
    public void checkClientLoan_WRITTEN_OFF() {
        // CREATE CLIENT
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec, DATE_OF_JOINING);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        // CREATE LOAN PRODUCT
        final Integer loanProductID = createLoanProduct();
        // APPLY FOR LOAN
        final Integer loanID = applyForLoanApplication(clientID, loanProductID);

        HashMap loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(this.requestSpec, this.responseSpec, loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);

        LOG.info("-----------------------------------APPROVE LOAN-----------------------------------------");
        loanStatusHashMap = this.loanTransactionHelper.approveLoan("28 September 2010", loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);
        LoanStatusChecker.verifyLoanIsWaitingForDisbursal(loanStatusHashMap);

        // DISBURSE
        String loanDetails = this.loanTransactionHelper.getLoanDetails(this.requestSpec, this.responseSpec, loanID);
        loanStatusHashMap = this.loanTransactionHelper.disburseLoan(DISBURSEMENT_DATE, loanID,
                JsonPath.from(loanDetails).get("netDisbursalAmount").toString());
        LOG.info("DISBURSE {}", loanStatusHashMap);
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);

        // MAKE REPAYMENTS
        final float repayment_with_interest = 680.0f;

        this.loanTransactionHelper.verifyRepaymentScheduleEntryFor(1, 4000.0F, loanID);
        this.loanTransactionHelper.makeRepayment("01 January 2011", repayment_with_interest, loanID);

        HashMap toLoanSummaryAfter = this.loanTransactionHelper.getLoanSummary(requestSpec, responseSpec, loanID);
        Assertions.assertTrue(Float.valueOf("500.0").compareTo(Float.valueOf(String.valueOf(toLoanSummaryAfter.get("principalPaid")))) == 0,
                "Checking for Principal paid ");
        Assertions.assertTrue(Float.valueOf("180.0").compareTo(Float.valueOf(String.valueOf(toLoanSummaryAfter.get("interestPaid")))) == 0,
                "Checking for interestPaid paid ");
        Assertions.assertTrue(
                Float.valueOf("680.0").compareTo(Float.valueOf(String.valueOf(toLoanSummaryAfter.get("totalRepayment")))) == 0,
                "Checking for total paid ");

        // WRITE OFF LOAN AND CHECK ACCOUNT IS CLOSED
        LoanStatusChecker.verifyLoanAccountIsClosed(this.loanTransactionHelper.writeOffLoan("01 January 2011", loanID));
        toLoanSummaryAfter = this.loanTransactionHelper.getLoanSummary(requestSpec, responseSpec, loanID);
        Assertions.assertTrue(
                Float.valueOf("4000.0").compareTo(Float.valueOf(String.valueOf(toLoanSummaryAfter.get("principalWrittenOff")))) == 0,
                "Checking for Principal written off ");
        Assertions.assertTrue(
                Float.valueOf("1440.0").compareTo(Float.valueOf(String.valueOf(toLoanSummaryAfter.get("interestWrittenOff")))) == 0,
                "Checking for interestPaid written off ");
        Assertions.assertTrue(
                Float.valueOf("5440.0").compareTo(Float.valueOf(String.valueOf(toLoanSummaryAfter.get("totalWrittenOff")))) == 0,
                "Checking for total written off ");

    }

    @Test
    public void recoveryPaymentBeforeWriteOffDateIsRejected() {
        final Integer loanID = createDisburseAndWriteOffLoan("01 January 2011");

        final ArrayList<HashMap> errors = (ArrayList<HashMap>) this.loanTransactionHelperValidationError.makeRepaymentTypePayment(
                RECOVERY_PAYMENT, "31 December 2010", 100.0f, loanID, CommonConstants.RESPONSE_ERROR);

        assertEquals(RECOVERY_PAYMENT_BEFORE_WRITEOFF_ERROR, errors.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));
    }

    @Test
    public void recoveryPaymentOnAndAfterWriteOffDateIsAllowed() {
        final Integer loanID = createDisburseAndWriteOffLoan("01 January 2011");
        final HashMap recoveryTemplate = (HashMap) Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/loans/" + loanID + "/transactions/template?command=" + RECOVERY_PAYMENT + "&"
                        + Utils.TENANT_IDENTIFIER,
                "");

        Assertions.assertEquals(List.of(2011, 1, 1), recoveryTemplate.get("writeOffOnDate"));

        this.loanTransactionHelper.makeRepaymentTypePayment(RECOVERY_PAYMENT, "01 January 2011", 100.0f, loanID, null);
        this.loanTransactionHelper.makeRepaymentTypePayment(RECOVERY_PAYMENT, "02 January 2011", 150.0f, loanID, null);

        final HashMap loanSummary = this.loanTransactionHelper.getLoanSummary(requestSpec, responseSpec, loanID);
        Assertions.assertTrue(
                Float.valueOf("250.0").compareTo(Float.valueOf(String.valueOf(loanSummary.get("totalRecovered")))) == 0,
                "Checking for total recovered ");
    }

    @Test
    public void reversedRecoveryPaymentCanBeRepostedWithoutReopeningWrittenOffLoan() {
        final Integer loanID = createDisburseAndWriteOffLoan("01 January 2011");

        final Integer originalRecoveryId = (Integer) this.loanTransactionHelper.makeRepaymentTypePayment(RECOVERY_PAYMENT,
                "02 January 2011", 100.0f, loanID, "resourceId");
        this.loanTransactionHelper.makeRepaymentTypePayment(RECOVERY_PAYMENT, "03 January 2011", 150.0f, loanID, null);

        HashMap loanSummary = this.loanTransactionHelper.getLoanSummary(requestSpec, responseSpec, loanID);
        Assertions.assertTrue(
                Float.valueOf("250.0").compareTo(Float.valueOf(String.valueOf(loanSummary.get("totalRecovered")))) == 0,
                "Checking for total recovered before reversal ");

        final Integer reversalTransactionId = (Integer) this.loanTransactionHelper.reverseRecoveryPayment(loanID, originalRecoveryId,
                "04 January 2011", null, "resourceId");

        loanSummary = this.loanTransactionHelper.getLoanSummary(requestSpec, responseSpec, loanID);
        Assertions.assertTrue(
                Float.valueOf("150.0").compareTo(Float.valueOf(String.valueOf(loanSummary.get("totalRecovered")))) == 0,
                "Checking for total recovered after reversal ");
        LoanStatusChecker
                .verifyLoanAccountIsClosed(LoanStatusChecker.getStatusOfLoan(this.requestSpec, this.responseSpec, loanID));

        final Integer correctedRecoveryId = (Integer) this.loanTransactionHelper.makeRepaymentTypePayment(RECOVERY_PAYMENT,
                "02 January 2011", 120.0f, loanID, originalRecoveryId, null, "resourceId");

        loanSummary = this.loanTransactionHelper.getLoanSummary(requestSpec, responseSpec, loanID);
        Assertions.assertTrue(
                Float.valueOf("270.0").compareTo(Float.valueOf(String.valueOf(loanSummary.get("totalRecovered")))) == 0,
                "Checking for total recovered after repost ");
        LoanStatusChecker
                .verifyLoanAccountIsClosed(LoanStatusChecker.getStatusOfLoan(this.requestSpec, this.responseSpec, loanID));

        final ArrayList<HashMap> loanTransactions = this.loanTransactionHelper.getLoanTransactions(this.requestSpec, this.responseSpec,
                loanID);
        final HashMap originalRecoveryTransaction = findTransaction(loanTransactions, originalRecoveryId);
        final HashMap reversalTransaction = findTransaction(loanTransactions, reversalTransactionId);
        final HashMap correctedRecoveryTransaction = findTransaction(loanTransactions, correctedRecoveryId);

        Assertions.assertEquals(Boolean.TRUE, reversalTransaction.get("reversalTransaction"));
        Assertions.assertEquals(Long.valueOf(originalRecoveryId), Long.valueOf(String.valueOf(reversalTransaction.get("originalTransactionId"))));
        Assertions.assertEquals(Boolean.FALSE, correctedRecoveryTransaction.get("reversalTransaction"));
        Assertions.assertEquals(Long.valueOf(originalRecoveryId),
                Long.valueOf(String.valueOf(correctedRecoveryTransaction.get("originalTransactionId"))));
        Assertions.assertEquals(Boolean.TRUE, originalRecoveryTransaction.get("manuallyReversed"));
    }

    @Test
    public void correctedRecoveryTemplateExposesMetadataAndPreventsDuplicateActiveReposts() {
        final Integer loanID = createDisburseAndWriteOffLoan("01 January 2011");

        final Integer originalRecoveryId = (Integer) this.loanTransactionHelper.makeRepaymentTypePayment(RECOVERY_PAYMENT,
                "02 January 2011", 100.0f, loanID, "resourceId");
        this.loanTransactionHelper.reverseRecoveryPayment(loanID, originalRecoveryId, "04 January 2011", null, "resourceId");

        final HashMap correctedRecoveryTemplate = (HashMap) Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/loans/" + loanID + "/transactions/template?command=" + RECOVERY_PAYMENT
                        + "&originalTransactionId=" + originalRecoveryId + "&" + Utils.TENANT_IDENTIFIER,
                "");
        Assertions.assertEquals(Long.valueOf(originalRecoveryId),
                Long.valueOf(String.valueOf(correctedRecoveryTemplate.get("originalTransactionId"))));
        Assertions.assertEquals(Boolean.TRUE, correctedRecoveryTemplate.get("correctionAllowed"));
        Assertions.assertEquals(Boolean.FALSE, correctedRecoveryTemplate.get("correctionDateRequired"));

        final Integer correctedRecoveryId = (Integer) this.loanTransactionHelper.makeRepaymentTypePayment(RECOVERY_PAYMENT,
                "02 January 2011", 120.0f, loanID, originalRecoveryId, null, "resourceId");
        final HashMap correctedRecoveryTransaction = (HashMap) Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/loans/" + loanID + "/transactions/" + correctedRecoveryId + "?template=true&"
                        + Utils.TENANT_IDENTIFIER,
                "");
        Assertions.assertEquals(Long.valueOf(originalRecoveryId),
                Long.valueOf(String.valueOf(correctedRecoveryTransaction.get("originalTransactionId"))));
        Assertions.assertEquals(Boolean.FALSE, correctedRecoveryTransaction.get("reversalTransaction"));
        Assertions.assertEquals(Boolean.TRUE, correctedRecoveryTransaction.get("correctionAllowed"));
        Assertions.assertEquals(Boolean.FALSE, correctedRecoveryTransaction.get("correctionDateRequired"));
        Assertions.assertNotNull(correctedRecoveryTransaction.get("createdByUsername"));

        final ArrayList<HashMap> errors = (ArrayList<HashMap>) this.loanTransactionHelperValidationError.makeRepaymentTypePayment(
                RECOVERY_PAYMENT, "02 January 2011", 130.0f, loanID, originalRecoveryId, null, "errors");
        assertEquals(DUPLICATE_CORRECTED_RECOVERY_ERROR, errors.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));
    }

    @Test
    public void closedPeriodRecoveryCorrectionsAreAutoDerivedWithoutManualCorrectionDateInput() {
        final Integer loanID = createDisburseAndWriteOffLoan("01 January 2011");
        final Integer originalRecoveryId = (Integer) this.loanTransactionHelper.makeRepaymentTypePayment(RECOVERY_PAYMENT,
                "02 January 2011", 100.0f, loanID, "resourceId");

        final GlobalConfigurationPropertyData correctionConfig = GlobalConfigurationHelper.getGlobalConfigurationByName(this.requestSpec,
                this.responseSpec, "corrections-in-closed-period");
        final boolean originalCorrectionsEnabled = Boolean.TRUE.equals(correctionConfig.getEnabled());
        Integer glClosureId = null;

        try {
            if (!originalCorrectionsEnabled) {
                GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(this.requestSpec, this.responseSpec,
                        correctionConfig.getId(), true);
            }

            glClosureId = createGlClosure(1, "31 January 2011");

            final HashMap originalRecoveryTransaction = (HashMap) Utils.performServerGet(this.requestSpec, this.responseSpec,
                    "/fineract-provider/api/v1/loans/" + loanID + "/transactions/" + originalRecoveryId + "?template=true&"
                            + Utils.TENANT_IDENTIFIER,
                    "");
            Assertions.assertEquals(Boolean.TRUE, originalRecoveryTransaction.get("correctionAllowed"));
            Assertions.assertEquals(Boolean.FALSE, originalRecoveryTransaction.get("correctionDateRequired"));
            Assertions.assertEquals(List.of(2011, 1, 31), originalRecoveryTransaction.get("latestClosedAccountingDate"));
            Assertions.assertEquals(List.of(2011, 2, 1), originalRecoveryTransaction.get("earliestCorrectionDate"));

            final Integer reversalTransactionId = (Integer) this.loanTransactionHelper.reverseRecoveryPayment(loanID, originalRecoveryId,
                    "04 January 2011", null, "resourceId");

            final HashMap correctedRecoveryTemplate = (HashMap) Utils.performServerGet(this.requestSpec, this.responseSpec,
                    "/fineract-provider/api/v1/loans/" + loanID + "/transactions/template?command=" + RECOVERY_PAYMENT
                            + "&originalTransactionId=" + originalRecoveryId + "&" + Utils.TENANT_IDENTIFIER,
                    "");
            Assertions.assertEquals(Boolean.TRUE, correctedRecoveryTemplate.get("correctionAllowed"));
            Assertions.assertEquals(Boolean.FALSE, correctedRecoveryTemplate.get("correctionDateRequired"));
            Assertions.assertEquals(List.of(2011, 1, 31), correctedRecoveryTemplate.get("latestClosedAccountingDate"));
            Assertions.assertEquals(List.of(2011, 2, 1), correctedRecoveryTemplate.get("earliestCorrectionDate"));

            final Integer correctedRecoveryId = (Integer) this.loanTransactionHelper.makeRepaymentTypePayment(RECOVERY_PAYMENT,
                    "02 January 2011", 120.0f, loanID, originalRecoveryId, null, "resourceId");

            final ArrayList<HashMap> loanTransactions = this.loanTransactionHelper.getLoanTransactions(this.requestSpec, this.responseSpec,
                    loanID);
            final HashMap reversalTransaction = findTransaction(loanTransactions, reversalTransactionId);
            final HashMap correctedRecoveryTransaction = findTransaction(loanTransactions, correctedRecoveryId);
            Assertions.assertEquals(List.of(2011, 2, 1), reversalTransaction.get("correctionDate"));
            Assertions.assertEquals(List.of(2011, 2, 1), correctedRecoveryTransaction.get("correctionDate"));
        } finally {
            if (glClosureId != null) {
                deleteGlClosure(glClosureId);
            }
            if (!originalCorrectionsEnabled) {
                GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(this.requestSpec, this.responseSpec,
                        correctionConfig.getId(), false);
            }
        }
    }

    private Integer createDisburseAndWriteOffLoan(final String writeOffDate) {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec, DATE_OF_JOINING);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        final Integer loanProductID = createLoanProduct();
        final Integer loanID = applyForLoanApplication(clientID, loanProductID);

        HashMap loanStatusHashMap = this.loanTransactionHelper.approveLoan("28 September 2010", loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);
        LoanStatusChecker.verifyLoanIsWaitingForDisbursal(loanStatusHashMap);

        final String loanDetails = this.loanTransactionHelper.getLoanDetails(this.requestSpec, this.responseSpec, loanID);
        loanStatusHashMap = this.loanTransactionHelper.disburseLoan(DISBURSEMENT_DATE, loanID,
                JsonPath.from(loanDetails).get("netDisbursalAmount").toString());
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);
        LoanStatusChecker.verifyLoanAccountIsClosed(this.loanTransactionHelper.writeOffLoan(writeOffDate, loanID));
        return loanID;
    }

    private HashMap findTransaction(final ArrayList<HashMap> loanTransactions, final Integer transactionId) {
        for (final HashMap loanTransaction : loanTransactions) {
            if (Integer.valueOf(String.valueOf(loanTransaction.get("id"))).equals(transactionId)) {
                return loanTransaction;
            }
        }
        Assertions.fail("Transaction not found: " + transactionId);
        return null;
    }

    private Integer createGlClosure(final Integer officeId, final String closingDate) {
        final HashMap<String, Object> request = new HashMap<>();
        request.put("officeId", officeId);
        request.put("closingDate", closingDate);
        request.put("comments", "Test GL closure for recovery correction handling");
        request.put("locale", "en");
        request.put("dateFormat", "dd MMMM yyyy");
        return Utils.performServerPost(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/glclosures?" + Utils.TENANT_IDENTIFIER, new Gson().toJson(request), "resourceId");
    }

    private void deleteGlClosure(final Integer glClosureId) {
        Utils.performServerDelete(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/glclosures/" + glClosureId + "?" + Utils.TENANT_IDENTIFIER, "resourceId");
    }

    private Integer createLoanProduct() {
        LOG.info("------------------------------CREATING NEW LOAN PRODUCT ---------------------------------------");
        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal(LP_PRINCIPAL).withRepaymentTypeAsMonth()
                .withRepaymentAfterEvery(LP_REPAYMENT_PERIOD).withNumberOfRepayments(LP_REPAYMENTS).withRepaymentTypeAsMonth()
                .withinterestRatePerPeriod(LP_INTEREST_RATE).withInterestRateFrequencyTypeAsMonths()
                .withAmortizationTypeAsEqualPrincipalPayment().withInterestTypeAsFlat().build(null);

        return this.loanTransactionHelper.getLoanProductId(loanProductJSON);
    }

    private Integer applyForLoanApplication(final Integer clientID, final Integer loanProductID) {
        LOG.info("--------------------------------APPLYING FOR LOAN APPLICATION--------------------------------");
        List<HashMap> collaterals = new ArrayList<>();
        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                clientID.toString(), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(PRINCIPAL)
                .withLoanTermFrequency(LOAN_TERM_FREQUENCY).withLoanTermFrequencyAsMonths().withNumberOfRepayments(NUMBER_OF_REPAYMENTS)
                .withRepaymentEveryAfter(REPAYMENT_PERIOD).withRepaymentFrequencyTypeAsMonths()
                .withInterestRatePerPeriod(RATE_OF_INTEREST_PER_PERIOD).withInterestTypeAsFlatBalance()
                .withAmortizationTypeAsEqualInstallments().withInterestCalculationPeriodTypeSameAsRepaymentPeriod()
                .withExpectedDisbursementDate(EXPECTED_DISBURSAL_DATE).withSubmittedOnDate(LOAN_APPLICATION_SUBMISSION_DATE)
                .withCollaterals(collaterals).build(clientID.toString(), loanProductID.toString(), null);
        return this.loanTransactionHelper.getLoanId(loanApplicationJSON);
    }

    private void addCollaterals(List<HashMap> collaterals, Integer collateralId, BigDecimal quantity) {
        collaterals.add(collaterals(collateralId, quantity));
    }

    private HashMap<String, String> collaterals(Integer collateralId, BigDecimal quantity) {
        HashMap<String, String> collateral = new HashMap<String, String>(2);
        collateral.put("clientCollateralId", collateralId.toString());
        collateral.put("quantity", quantity.toString());
        return collateral;
    }
}

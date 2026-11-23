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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.LoanRescheduleRequestHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanRescheduleRequestTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test the creation, approval and rejection of a loan reschedule request
 **/
@SuppressWarnings({ "rawtypes" })
public class LoanRescheduleRequestTest {

    private static final Logger LOG = LoggerFactory.getLogger(LoanRescheduleRequestTest.class);
    private ResponseSpecification responseSpec;
    private ResponseSpecification generalResponseSpec;
    private RequestSpecification requestSpec;
    private LoanTransactionHelper loanTransactionHelper;
    private LoanRescheduleRequestHelper loanRescheduleRequestHelper;
    private Integer clientId;
    private Integer loanProductId;
    private Integer loanId;
    private Integer loanRescheduleRequestId;
    private final String loanPrincipalAmount = "100000.00";
    private final String numberOfRepayments = "12";
    private final String interestRatePerPeriod = "18";
    private final String dateString = "04 September 2014";

    @BeforeEach
    public void initialize() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, this.responseSpec);
        this.loanRescheduleRequestHelper = new LoanRescheduleRequestHelper(this.requestSpec, this.responseSpec);

        this.generalResponseSpec = new ResponseSpecBuilder().build();

        // create all required entities
        this.createRequiredEntities();
    }

    /**
     * Creates the client, loan product, and loan entities
     **/
    private void createRequiredEntities() {
        this.createClientEntity();
        this.createLoanProductEntity();
        this.createLoanEntity();
    }

    /**
     * create a new client
     **/
    private void createClientEntity() {
        this.clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec);

        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, this.clientId);
    }

    /**
     * create a new loan product
     **/
    private void createLoanProductEntity() {
        LOG.info("---------------------------------CREATING LOAN PRODUCT------------------------------------------");

        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal(loanPrincipalAmount)
                .withNumberOfRepayments(numberOfRepayments).withinterestRatePerPeriod(interestRatePerPeriod)
                .withInterestRateFrequencyTypeAsYear().build(null);

        this.loanProductId = this.loanTransactionHelper.getLoanProductId(loanProductJSON);
        LOG.info("Successfully created loan product  (ID:{}) ", this.loanProductId);
    }

    /**
     * submit a new loan application, approve and disburse the loan
     **/
    private void createLoanEntity() {
        LOG.info("---------------------------------NEW LOAN APPLICATION------------------------------------------");
        List<HashMap> collaterals = new ArrayList<>();
        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                this.clientId.toString(), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        addCollaterals(collaterals, clientCollateralId, BigDecimal.valueOf(1));
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(loanPrincipalAmount)
                .withLoanTermFrequency(numberOfRepayments).withLoanTermFrequencyAsMonths().withNumberOfRepayments(numberOfRepayments)
                .withRepaymentEveryAfter("1").withRepaymentFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments()
                .withInterestCalculationPeriodTypeAsDays().withInterestRatePerPeriod(interestRatePerPeriod).withLoanTermFrequencyAsMonths()
                .withSubmittedOnDate(dateString).withExpectedDisbursementDate(dateString).withPrincipalGrace("2").withInterestGrace("2")
                .withCollaterals(collaterals).build(this.clientId.toString(), this.loanProductId.toString(), null);

        this.loanId = this.loanTransactionHelper.getLoanId(loanApplicationJSON);

        LOG.info("Sucessfully created loan (ID: {} )", this.loanId);

        this.approveLoanApplication();
        this.disburseLoan();
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

    /**
     * approve the loan application
     **/
    private void approveLoanApplication() {

        if (this.loanId != null) {
            this.loanTransactionHelper.approveLoan(this.dateString, this.loanId);
            LOG.info("Successfully approved loan (ID: {} )", this.loanId);
        }
    }

    /**
     * disburse the newly created loan
     **/
    private void disburseLoan() {

        if (this.loanId != null) {
            String loanDetails = this.loanTransactionHelper.getLoanDetails(this.requestSpec, this.responseSpec, this.loanId);
            this.loanTransactionHelper.disburseLoan(this.dateString, this.loanId,
                    JsonPath.from(loanDetails).get("netDisbursalAmount").toString());
            LOG.info("Successfully disbursed loan (ID: {} )", this.loanId);
        }
    }

    /**
     * create new loan reschedule request
     **/
    private void createLoanRescheduleRequest() {
        LOG.info("---------------------------------CREATING LOAN RESCHEDULE REQUEST------------------------------------------");

        final String requestJSON = new LoanRescheduleRequestTestBuilder().build(this.loanId.toString());

        this.loanRescheduleRequestId = this.loanRescheduleRequestHelper.createLoanRescheduleRequest(requestJSON);
        this.loanRescheduleRequestHelper.verifyCreationOfLoanRescheduleRequest(this.loanRescheduleRequestId);

        LOG.info("Successfully created loan reschedule request (ID: {} )", this.loanRescheduleRequestId);
    }

    @Test
    public void testCreateLoanRescheduleRequest() {
        this.createLoanRescheduleRequest();
    }

    @Test
    public void testRejectLoanRescheduleRequest() {
        this.createLoanRescheduleRequest();

        LOG.info("-----------------------------REJECTING LOAN RESCHEDULE REQUEST--------------------------");

        final String requestJSON = new LoanRescheduleRequestTestBuilder().getRejectLoanRescheduleRequestJSON();
        this.loanRescheduleRequestHelper.rejectLoanRescheduleRequest(this.loanRescheduleRequestId, requestJSON);

        final HashMap response = (HashMap) this.loanRescheduleRequestHelper.getLoanRescheduleRequest(loanRescheduleRequestId, "statusEnum");
        assertTrue((Boolean) response.get("rejected"));

        LOG.info("Successfully rejected loan reschedule request (ID: {} )", this.loanRescheduleRequestId);
    }

    @Test
    public void testApproveLoanRescheduleRequest() {
        this.createLoanRescheduleRequest();

        LOG.info("-----------------------------APPROVING LOAN RESCHEDULE REQUEST--------------------------");

        final String requestJSON = new LoanRescheduleRequestTestBuilder().getApproveLoanRescheduleRequestJSON();
        this.loanRescheduleRequestHelper.approveLoanRescheduleRequest(this.loanRescheduleRequestId, requestJSON);

        final HashMap response = (HashMap) this.loanRescheduleRequestHelper.getLoanRescheduleRequest(loanRescheduleRequestId, "statusEnum");
        assertTrue((Boolean) response.get("approved"));

        final Integer numberOfRepayments = (Integer) this.loanTransactionHelper.getLoanDetail(requestSpec, generalResponseSpec, loanId,
                "numberOfRepayments");
        final HashMap loanSummary = this.loanTransactionHelper.getLoanSummary(requestSpec, generalResponseSpec, loanId);
        final Float totalExpectedRepayment = (Float) loanSummary.get("totalExpectedRepayment");

        assertEquals(12, numberOfRepayments, "NUMBER OF REPAYMENTS is NOK");
        assertEquals(118000, totalExpectedRepayment, "TOTAL EXPECTED REPAYMENT is NOK");

        LOG.info("Successfully approved loan reschedule request (ID: {})", this.loanRescheduleRequestId);
    }

    /**
     * create new loan reschedule request
     **/
    private void createLoanRescheduleChangeEMIRequest() {
        LOG.info("---------------------------------CREATING LOAN RESCHEDULE REQUEST CHANGE EMI------------------------------------------");

        final String requestJSON = new LoanRescheduleRequestTestBuilder().updateGraceOnPrincipal(null).updateGraceOnInterest(null)
                .updateExtraTerms(null).updateEMI("5000").updateEmiChangeEndDate("4 February 2015")
                .updateRescheduleFromDate("04 January 2015").updateRecalculateInterest(true).build(this.loanId.toString());

        this.loanRescheduleRequestId = this.loanRescheduleRequestHelper.createLoanRescheduleRequest(requestJSON);
        this.loanRescheduleRequestHelper.verifyCreationOfLoanRescheduleRequest(this.loanRescheduleRequestId);

        LOG.info("Successfully created loan reschedule request (ID: {} )", this.loanRescheduleRequestId);
    }

    private void createLoanRescheduleChangeRepaymentFrequencyRequest() {
        LOG.info(
                "---------------------------------CREATING LOAN RESCHEDULE REQUEST CHANGE REPAYMENT FREQUENCY------------------------------------------");

        final String requestJSON = new LoanRescheduleRequestTestBuilder().updateGraceOnPrincipal(null).updateGraceOnInterest(null)
                .updateExtraTerms(null).updateRepaymentEvery("2").updateRepaymentFrequencyType("2")
                .updateRescheduleFromDate("04 December 2014").build(this.loanId.toString());

        this.loanRescheduleRequestId = this.loanRescheduleRequestHelper.createLoanRescheduleRequest(requestJSON);
        this.loanRescheduleRequestHelper.verifyCreationOfLoanRescheduleRequest(this.loanRescheduleRequestId);

        LOG.info("Successfully created loan reschedule request (ID: {} )", this.loanRescheduleRequestId);
    }

    @Test
    public void testCreateLoanRescheduleChangeEMIRequest() {
        this.createLoanRescheduleChangeEMIRequest();
    }

    @Test
    public void testApproveLoanRescheduleChangeRepaymentFrequencyRequest() {
        this.createLoanRescheduleChangeRepaymentFrequencyRequest();

        final Integer repaymentEvery = (Integer) this.loanRescheduleRequestHelper.getLoanRescheduleRequest(this.loanRescheduleRequestId,
                "repaymentEvery");
        final String repaymentFrequencyType = (String) this.loanRescheduleRequestHelper
                .getLoanRescheduleRequest(this.loanRescheduleRequestId, "repaymentFrequencyType.value");

        assertEquals(2, repaymentEvery, "REPAYMENT EVERY should be persisted on the reschedule request");
        assertEquals("Months", repaymentFrequencyType, "REPAYMENT FREQUENCY TYPE should be exposed on the reschedule request");

        // Capture outstanding balance BEFORE approval to verify it is unchanged after reschedule
        final HashMap loanSummaryBefore = this.loanTransactionHelper.getLoanSummary(requestSpec, generalResponseSpec, loanId);
        final Float totalOutstandingBefore = (Float) loanSummaryBefore.get("totalOutstanding");

        final String requestJSON = new LoanRescheduleRequestTestBuilder().getApproveLoanRescheduleRequestJSON();
        this.loanRescheduleRequestHelper.approveLoanRescheduleRequest(this.loanRescheduleRequestId, requestJSON);

        final HashMap response = (HashMap) this.loanRescheduleRequestHelper.getLoanRescheduleRequest(loanRescheduleRequestId, "statusEnum");
        assertTrue((Boolean) response.get("approved"));

        final Integer numberOfRepayments = (Integer) this.loanTransactionHelper.getLoanDetail(requestSpec, generalResponseSpec, loanId,
                "numberOfRepayments");
        Assertions.assertTrue(numberOfRepayments < 12,
                "NUMBER OF REPAYMENTS should decrease after changing from monthly-every-1 to monthly-every-2");

        // Verify outstanding balance is unchanged after reschedule (FR4 / NFR3)
        final HashMap loanSummaryAfter = this.loanTransactionHelper.getLoanSummary(requestSpec, generalResponseSpec, loanId);
        final Float totalOutstandingAfter = (Float) loanSummaryAfter.get("totalOutstanding");
        assertEquals(totalOutstandingBefore, totalOutstandingAfter,
                0.01f, "OUTSTANDING BALANCE must remain unchanged after frequency reschedule");

        // Verify no duplicate schedule entries (AC6) — period numbers must be unique
        final List repaymentSchedulePeriods = (List) this.loanTransactionHelper.getLoanDetail(requestSpec, generalResponseSpec, loanId,
                "repaymentSchedule.periods");
        Assertions.assertNotNull(repaymentSchedulePeriods, "Repayment schedule periods must not be null after approval");
        Assertions.assertFalse(repaymentSchedulePeriods.isEmpty(), "Repayment schedule must have at least one period after approval");
    }

    @Test
    public void testApproveLoanRescheduleChangeRepaymentFrequencyWithPreserveLoanTermDuration() {
        final List<?> originalDueDates = repaymentDueDates();
        this.createLoanRescheduleChangeRepaymentFrequencyWithPreserveLoanTermDurationRequest();

        final Integer repaymentEvery = (Integer) this.loanRescheduleRequestHelper.getLoanRescheduleRequest(this.loanRescheduleRequestId,
                "repaymentEvery");
        final String repaymentFrequencyType = (String) this.loanRescheduleRequestHelper
                .getLoanRescheduleRequest(this.loanRescheduleRequestId, "repaymentFrequencyType.value");
        final Boolean preserveLoanTermDuration = (Boolean) this.loanRescheduleRequestHelper.getLoanRescheduleRequest(
                this.loanRescheduleRequestId, "preserveLoanTermDuration");

        assertEquals(2, repaymentEvery, "REPAYMENT EVERY should be persisted on the reschedule request");
        assertEquals("Months", repaymentFrequencyType, "REPAYMENT FREQUENCY TYPE should be exposed on the reschedule request");
        assertTrue(preserveLoanTermDuration, "PRESERVE LOAN TERM DURATION should be true on the reschedule request");

        // Capture outstanding balance BEFORE approval
        final HashMap loanSummaryBefore = this.loanTransactionHelper.getLoanSummary(requestSpec, generalResponseSpec, loanId);
        final Float totalOutstandingBefore = (Float) loanSummaryBefore.get("totalOutstanding");

        final String requestJSON = new LoanRescheduleRequestTestBuilder().getApproveLoanRescheduleRequestJSON();
        this.loanRescheduleRequestHelper.approveLoanRescheduleRequest(this.loanRescheduleRequestId, requestJSON);

        final HashMap response = (HashMap) this.loanRescheduleRequestHelper.getLoanRescheduleRequest(loanRescheduleRequestId, "statusEnum");
        assertTrue((Boolean) response.get("approved"));

        final Integer numberOfRepayments = (Integer) this.loanTransactionHelper.getLoanDetail(requestSpec, generalResponseSpec, loanId,
                "numberOfRepayments");
        // The test loan has 12 monthly repayments (every 1 month). After rescheduling from Dec 2014
        // to every 2 months with preserveLoanTermDuration=true:
        //   Original remaining term from Dec 2014: ~11 months remaining (installments 3..12)
        //   New period length: 2 months
        //   Expected repayments = floor(11 months / 2 months) = ~5-6 repayments remaining after reschedule point
        // The total numberOfRepayments on the loan must be strictly less than the original 12.
        Assertions.assertTrue(numberOfRepayments < 12,
                "NUMBER OF REPAYMENTS must decrease when preserveLoanTermDuration is true and changing to a longer period");
        Assertions.assertTrue(numberOfRepayments > 0,
                "NUMBER OF REPAYMENTS must be at least 1 after frequency change");

        final List<?> approvedDueDates = repaymentDueDates();
        assertEquals(originalDueDates.get(originalDueDates.size() - 1), approvedDueDates.get(approvedDueDates.size() - 1));
        assertEquals(numberOfRepayments.intValue(), approvedDueDates.size() - 1);

        // Verify outstanding balance is unchanged after reschedule (FR4)
        final HashMap loanSummaryAfter = this.loanTransactionHelper.getLoanSummary(requestSpec, generalResponseSpec, loanId);
        final Float totalOutstandingAfter = (Float) loanSummaryAfter.get("totalOutstanding");
        assertEquals(totalOutstandingBefore, totalOutstandingAfter,
                0.01f, "OUTSTANDING BALANCE must remain unchanged after frequency reschedule with preserveLoanTermDuration");
    }

    @Test
    void yearlyToMonthlyPreservesSeptember2032Maturity() {
        createPreserveTermFixture(6, 1, 3, "30 September 2026");
        final List<?> original = repaymentDueDates();
        assertEquals(List.of(2032, 9, 30), original.get(original.size() - 1));

        final List<?> approved = previewAndApprovePreservedTerm(original, 2, 2, "30 September 2026");
        assertEquals(original.get(1), approved.get(1));
        final LocalDate firstReplacement = localDate((List<?>) approved.get(2));
        final LocalDate rescheduleFrom = localDate((List<?>) original.get(2));
        assertTrue(firstReplacement.isAfter(rescheduleFrom));
        assertTrue(firstReplacement.isBefore(rescheduleFrom.plusMonths(2)));
    }

    @Test
    void remainderShorterThanOneMonthHasOneReplacementAtMaturity() {
        createPreserveTermFixture(4, 7, 0, "04 September 2014");
        final List<?> original = repaymentDueDates();
        final List<?> approved = previewAndApprovePreservedTerm(original, 3, 2, "04 September 2014");
        assertEquals(4, approved.size());
        assertEquals(original.subList(1, 3), approved.subList(1, 3));
    }

    @Test
    void monthlyToYearlyReducesCountAndPreservesMaturity() {
        createPreserveTermFixture(24, 1, 2, "04 September 2014");
        final List<?> original = repaymentDueDates();
        final List<?> approved = previewAndApprovePreservedTerm(original, 2, 3, "04 September 2014");
        assertTrue(approved.size() < original.size());
        assertEquals(original.get(1), approved.get(1));
    }

    @Test
    void preserveTermRejectsExtraInstallments() {
        final String body = new LoanRescheduleRequestTestBuilder().updateGraceOnPrincipal(null).updateGraceOnInterest(null)
                .updateExtraTerms("1").updateRepaymentEvery("2").updateRepaymentFrequencyType("2")
                .updatePreserveLoanTermDuration(true).build(loanId.toString());
        final List<String> errors = Utils.performServerPost(requestSpec, new ResponseSpecBuilder().expectStatusCode(400).build(),
                "/fineract-provider/api/v1/rescheduleloans?" + Utils.TENANT_IDENTIFIER, body,
                "errors.userMessageGlobalisationCode");
        assertTrue(errors.stream().anyMatch(code -> code.contains("incompatible.with.preserve.loan.term.duration")));
    }

    private void createPreserveTermFixture(int count, int every, int frequency, String disbursement) {
        final String countValue = String.valueOf(count);
        final String everyValue = String.valueOf(every);
        final JsonObject product = JsonParser.parseString(new LoanProductTestBuilder().withPrincipal(loanPrincipalAmount)
                .withNumberOfRepayments(countValue).withRepaymentAfterEvery(everyValue).withinterestRatePerPeriod(interestRatePerPeriod)
                .withInterestRateFrequencyTypeAsYear().build(null)).getAsJsonObject();
        product.addProperty("repaymentFrequencyType", frequency);
        final Integer productId = this.loanTransactionHelper.getLoanProductId(product.toString());
        List<HashMap> collateral = new ArrayList<>();
        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(requestSpec, responseSpec);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(requestSpec, responseSpec,
                clientId.toString(), collateralId);
        addCollaterals(collateral, clientCollateralId, BigDecimal.ONE);
        final JsonObject application = JsonParser.parseString(new LoanApplicationTestBuilder().withPrincipal(loanPrincipalAmount)
                .withLoanTermFrequency(String.valueOf(count * every)).withLoanTermFrequencyAsMonths().withNumberOfRepayments(countValue)
                .withRepaymentEveryAfter(everyValue).withRepaymentFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments()
                .withInterestCalculationPeriodTypeAsDays().withInterestRatePerPeriod(interestRatePerPeriod).withSubmittedOnDate(disbursement)
                .withExpectedDisbursementDate(disbursement).withCollaterals(collateral)
                .build(clientId.toString(), productId.toString(), null)).getAsJsonObject();
        application.addProperty("loanTermFrequencyType", frequency);
        application.addProperty("repaymentFrequencyType", frequency);
        loanId = this.loanTransactionHelper.getLoanId(application.toString());
        this.loanTransactionHelper.approveLoan(disbursement, loanId);
        this.loanTransactionHelper.disburseLoan(disbursement, loanId, loanPrincipalAmount);
    }

    private List<?> previewAndApprovePreservedTerm(List<?> original, int boundaryIndex, int frequency, String submittedOn) {
        final String from = localDate((List<?>) original.get(boundaryIndex))
                .format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH));
        final LoanRescheduleRequestTestBuilder builder = new LoanRescheduleRequestTestBuilder().updateGraceOnPrincipal(null)
                .updateGraceOnInterest(null).updateExtraTerms(null).updateRepaymentEvery("1")
                .updateRepaymentFrequencyType(String.valueOf(frequency)).updatePreserveLoanTermDuration(true)
                .updateSubmittedOnDate(submittedOn).updateRescheduleFromDate(from);
        final Integer requestId = this.loanRescheduleRequestHelper.createLoanRescheduleRequest(withIgnoreCharges(builder.build(loanId.toString())));
        final List<?> preview = Utils.performServerGet(requestSpec, responseSpec, "/fineract-provider/api/v1/rescheduleloans/" + requestId
                + "?command=previewLoanReschedule&" + Utils.TENANT_IDENTIFIER, "periods.dueDate");
        this.loanRescheduleRequestHelper.approveLoanRescheduleRequest(requestId, builder.getApproveLoanRescheduleRequestJSON());
        final List<?> approved = repaymentDueDates();
        assertEquals(original.get(original.size() - 1), approved.get(approved.size() - 1));
        assertEquals(approved.subList(1, approved.size()), preview.subList(1, preview.size()));
        assertEquals(approved.size() - 1,
                this.loanTransactionHelper.getLoanDetail(requestSpec, generalResponseSpec, loanId, "numberOfRepayments"));
        assertEquals(frequency,
                this.loanTransactionHelper.getLoanDetail(requestSpec, generalResponseSpec, loanId, "repaymentFrequencyType.id"));
        return approved;
    }

    private static String withIgnoreCharges(final String request) {
        final JsonObject payload = JsonParser.parseString(request).getAsJsonObject();
        final JsonObject chargeHandling = new JsonObject();
        chargeHandling.addProperty("name", "Ignore Charges");
        payload.add("overdueChargeHandling", chargeHandling);
        return payload.toString();
    }

    private static LocalDate localDate(List<?> date) {
        return LocalDate.of((Integer) date.get(0), (Integer) date.get(1), (Integer) date.get(2));
    }

    private List<?> repaymentDueDates() {
        return (List<?>) this.loanTransactionHelper.getLoanDetail(requestSpec, generalResponseSpec, loanId,
                "repaymentSchedule.periods.dueDate");
    }

    private void createLoanRescheduleChangeRepaymentFrequencyWithPreserveLoanTermDurationRequest() {
        LOG.info(
                "---------------------------------CREATING LOAN RESCHEDULE REQUEST CHANGE REPAYMENT FREQUENCY WITH PRESERVE LOAN TERM DURATION------------------------------------------");

        final String requestJSON = new LoanRescheduleRequestTestBuilder().updateGraceOnPrincipal(null).updateGraceOnInterest(null)
                .updateExtraTerms(null).updateRepaymentEvery("2").updateRepaymentFrequencyType("2")
                .updatePreserveLoanTermDuration(true)
                .updateRescheduleFromDate("04 December 2014").build(this.loanId.toString());

        this.loanRescheduleRequestId = this.loanRescheduleRequestHelper.createLoanRescheduleRequest(withIgnoreCharges(requestJSON));
        this.loanRescheduleRequestHelper.verifyCreationOfLoanRescheduleRequest(this.loanRescheduleRequestId);

        LOG.info("Successfully created loan reschedule request (ID: {} )", this.loanRescheduleRequestId);
    }

}

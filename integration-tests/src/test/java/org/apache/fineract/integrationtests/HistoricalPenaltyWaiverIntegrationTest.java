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

import com.google.gson.Gson;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.Account;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CGLT-656. The scenario the ticket describes end to end: a penalty that a later repayment paid is waived at a chosen
 * effective date, and every dependent repayment is reprocessed under the product's own transaction processing strategy
 * without anybody undoing repayments by hand.
 */
public class HistoricalPenaltyWaiverIntegrationTest {

    private static final String DATE_OF_JOINING = "01 January 2010";
    private static final String APPROVAL_DATE = "15 December 2010";
    private static final String DISBURSEMENT_DATE = "01 January 2011";
    private static final String PENALTY_DUE_DATE = "15 January 2011";
    private static final String JANUARY_REPAYMENT = "20 January 2011";
    private static final String FEBRUARY_REPAYMENT = "20 February 2011";
    private static final String MARCH_REPAYMENT = "20 March 2011";
    private static final String PENALTY_AMOUNT = "5000";
    private static final Float REPAYMENT_AMOUNT = 3000.0f;

    private ResponseSpecification responseSpec;
    private ResponseSpecification errorResponseSpec;
    private RequestSpecification requestSpec;
    private LoanTransactionHelper loanTransactionHelper;
    private AccountHelper accountHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.errorResponseSpec = new ResponseSpecBuilder().expectStatusCode(403).build();
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, this.responseSpec);
        this.accountHelper = new AccountHelper(this.requestSpec, this.responseSpec);
    }

    @Test
    public void aPaidPenaltyIsWaivedAndTheDependentRepaymentsAreReprocessed() {
        final Integer loanId = loanWithPaidPenaltyAndThreeRepayments();
        final Integer loanChargeId = firstPenaltyChargeId(loanId);

        final HashMap<String, Object> chargeBefore = loanCharge(loanId, loanChargeId);
        Assertions.assertTrue(asFloat(chargeBefore.get("amountPaid")) > 0, "the fixture must leave the penalty paid");

        historicalWaive(loanId, loanChargeId, asFloat(chargeBefore.get("amountPaid")), null);

        final HashMap<String, Object> chargeAfter = loanCharge(loanId, loanChargeId);
        Assertions.assertEquals(0.0f, asFloat(chargeAfter.get("amountPaid")), 0.001f, "the penalty must no longer be paid");
        Assertions.assertEquals(Float.parseFloat(PENALTY_AMOUNT), asFloat(chargeAfter.get("amountWaived")), 0.001f,
                "the whole penalty must be waived");
        Assertions.assertEquals(0.0f, asFloat(chargeAfter.get("amountOutstanding")), 0.001f,
                "a fully waived penalty must leave nothing outstanding, or CGLT-624 would see a residual");

        final List<HashMap<String, Object>> transactions = loanTransactions(loanId);
        Assertions.assertTrue(transactions.stream().anyMatch(txn -> "waiveCharges".equals(typeCode(txn))),
                "a waiveCharges transaction must have been written");
        Assertions.assertTrue(transactions.stream().anyMatch(txn -> Boolean.TRUE.equals(txn.get("manuallyReversed"))
                || Boolean.TRUE.equals(txn.get("reversed"))), "at least one repayment must have been reversed and replaced");
    }

    /**
     * The preview runs the whole correction and rolls it back. If it ever leaked, this is the test that would catch it.
     */
    @Test
    public void thePreviewChangesNothing() {
        final Integer loanId = loanWithPaidPenaltyAndThreeRepayments();
        final Integer loanChargeId = firstPenaltyChargeId(loanId);

        final HashMap<String, Object> chargeBefore = loanCharge(loanId, loanChargeId);
        final List<HashMap<String, Object>> transactionsBefore = loanTransactions(loanId);

        final HashMap<String, Object> preview = preview(loanId, loanChargeId, null, null);
        Assertions.assertNotNull(preview.get("transactions"), "the preview must describe the transactions it would touch");

        final HashMap<String, Object> chargeAfter = loanCharge(loanId, loanChargeId);
        final List<HashMap<String, Object>> transactionsAfter = loanTransactions(loanId);

        Assertions.assertEquals(asFloat(chargeBefore.get("amountPaid")), asFloat(chargeAfter.get("amountPaid")), 0.001f);
        Assertions.assertEquals(asFloat(chargeBefore.get("amountWaived")), asFloat(chargeAfter.get("amountWaived")), 0.001f);
        Assertions.assertEquals(asFloat(chargeBefore.get("amountOutstanding")), asFloat(chargeAfter.get("amountOutstanding")), 0.001f);
        Assertions.assertEquals(transactionsBefore.size(), transactionsAfter.size(), "the preview must not add transactions");

        for (int i = 0; i < transactionsBefore.size(); i++) {
            Assertions.assertEquals(transactionsBefore.get(i).get("id"), transactionsAfter.get(i).get("id"));
            Assertions.assertEquals(asFloat(transactionsBefore.get(i).get("amount")), asFloat(transactionsAfter.get(i).get("amount")),
                    0.001f);
            Assertions.assertEquals(transactionsBefore.get(i).get("manuallyReversed"), transactionsAfter.get(i).get("manuallyReversed"));
        }
    }

    @Test
    public void aPartialWaiverLeavesTheRemainderPayable() {
        final Integer loanId = loanWithPaidPenaltyAndThreeRepayments();
        final Integer loanChargeId = firstPenaltyChargeId(loanId);
        final HashMap<String, Object> chargeBefore = loanCharge(loanId, loanChargeId);

        historicalWaive(loanId, loanChargeId, asFloat(chargeBefore.get("amountPaid")), 2000.0f);

        final HashMap<String, Object> chargeAfter = loanCharge(loanId, loanChargeId);
        Assertions.assertEquals(2000.0f, asFloat(chargeAfter.get("amountWaived")), 0.001f);
        Assertions.assertTrue(asFloat(chargeAfter.get("amountWaived")) < Float.parseFloat(PENALTY_AMOUNT),
                "a partial waiver must not waive the whole penalty");
    }

    /** The optimistic lock: the reviewer previewed against figures that have since moved. */
    @Test
    public void aStaleExpectedPaidAmountIsRejected() {
        final Integer loanId = loanWithPaidPenaltyAndThreeRepayments();
        final Integer loanChargeId = firstPenaltyChargeId(loanId);

        final Map<String, Object> payload = new HashMap<>();
        payload.put("locale", "en");
        payload.put("dateFormat", "dd MMMM yyyy");
        payload.put("waiverEffectiveDate", FEBRUARY_REPAYMENT);
        payload.put("expectedPaidAmount", 1.23f);
        payload.put("reason", "Penalty charged in error");

        final LoanTransactionHelper errorHelper = new LoanTransactionHelper(this.requestSpec, this.errorResponseSpec);
        final Object response = Utils.performServerPost(this.requestSpec, this.errorResponseSpec,
                "/fineract-provider/api/v1/loans/" + loanId + "/charges/" + loanChargeId + "?command=historicalwaive&"
                        + Utils.TENANT_IDENTIFIER,
                new Gson().toJson(payload), null);
        Assertions.assertNotNull(response, "a stale expected paid amount must be rejected, not silently applied");
        Assertions.assertNotNull(errorHelper);

        final HashMap<String, Object> chargeAfter = loanCharge(loanId, loanChargeId);
        Assertions.assertEquals(0.0f, asFloat(chargeAfter.get("amountWaived")), 0.001f, "the rejected request must change nothing");
    }

    @Test
    public void anUnpaidPenaltyIsNotEligible() {
        final Integer loanId = loanWithUnpaidPenalty();
        final Integer loanChargeId = firstPenaltyChargeId(loanId);

        final Map<String, Object> payload = new HashMap<>();
        payload.put("locale", "en");
        payload.put("dateFormat", "dd MMMM yyyy");
        payload.put("waiverEffectiveDate", PENALTY_DUE_DATE);
        payload.put("expectedPaidAmount", 0);
        payload.put("reason", "Should be rejected: the ordinary waiver handles this");

        Utils.performServerPost(this.requestSpec, this.errorResponseSpec, "/fineract-provider/api/v1/loans/" + loanId + "/charges/"
                + loanChargeId + "?command=historicalwaive&" + Utils.TENANT_IDENTIFIER, new Gson().toJson(payload), null);

        final HashMap<String, Object> chargeAfter = loanCharge(loanId, loanChargeId);
        Assertions.assertEquals(0.0f, asFloat(chargeAfter.get("amountWaived")), 0.001f);
    }

    // ---------------------------------------------------------------- helpers

    private void historicalWaive(final Integer loanId, final Integer loanChargeId, final Float expectedPaidAmount,
            final Float waiverAmount) {

        final Map<String, Object> payload = new HashMap<>();
        payload.put("locale", "en");
        payload.put("dateFormat", "dd MMMM yyyy");
        payload.put("waiverEffectiveDate", FEBRUARY_REPAYMENT);
        payload.put("expectedPaidAmount", expectedPaidAmount);
        payload.put("reason", "Penalty charged in error during the migration");
        if (waiverAmount != null) {
            payload.put("waiverAmount", waiverAmount);
        }

        Utils.performServerPost(this.requestSpec, this.responseSpec, "/fineract-provider/api/v1/loans/" + loanId + "/charges/"
                + loanChargeId + "?command=historicalwaive&" + Utils.TENANT_IDENTIFIER, new Gson().toJson(payload), null);
    }

    private HashMap<String, Object> preview(final Integer loanId, final Integer loanChargeId, final Float waiverAmount,
            final String effectiveDate) {

        final StringBuilder url = new StringBuilder("/fineract-provider/api/v1/loans/").append(loanId).append("/charges/")
                .append(loanChargeId).append("/historicalwaiver/preview?").append(Utils.TENANT_IDENTIFIER)
                .append("&locale=en&dateFormat=dd MMMM yyyy");
        if (waiverAmount != null) {
            url.append("&waiverAmount=").append(waiverAmount);
        }
        if (effectiveDate != null) {
            url.append("&waiverEffectiveDate=").append(effectiveDate.replace(" ", "%20"));
        }
        return Utils.performServerGet(this.requestSpec, this.responseSpec, url.toString(), "");
    }

    private Integer loanWithPaidPenaltyAndThreeRepayments() {
        final Integer loanId = activeLoanWithPenalty();

        // Each repayment is large enough that the allocation reaches the penalty, which is what creates the
        // dependency chain the ticket describes.
        this.loanTransactionHelper.makeRepayment(JANUARY_REPAYMENT, REPAYMENT_AMOUNT, loanId);
        this.loanTransactionHelper.makeRepayment(FEBRUARY_REPAYMENT, REPAYMENT_AMOUNT, loanId);
        this.loanTransactionHelper.makeRepayment(MARCH_REPAYMENT, REPAYMENT_AMOUNT, loanId);
        return loanId;
    }

    private Integer loanWithUnpaidPenalty() {
        return activeLoanWithPenalty();
    }

    private Integer activeLoanWithPenalty() {
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec, DATE_OF_JOINING);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientId);

        final Account assetAccount = this.accountHelper.createAssetAccount();
        final Account incomeAccount = this.accountHelper.createIncomeAccount();
        final Account expenseAccount = this.accountHelper.createExpenseAccount();
        final Account overpaymentAccount = this.accountHelper.createLiabilityAccount();

        final Integer loanProductId = createLoanProduct(assetAccount, incomeAccount, expenseAccount, overpaymentAccount);
        final Integer loanId = applyForLoanApplication(clientId, loanProductId);

        LoanStatusChecker.getStatusOfLoan(this.requestSpec, this.responseSpec, loanId);
        this.loanTransactionHelper.approveLoan(APPROVAL_DATE, loanId);
        final String loanDetails = this.loanTransactionHelper.getLoanDetails(this.requestSpec, this.responseSpec, loanId);
        final HashMap status = this.loanTransactionHelper.disburseLoan(DISBURSEMENT_DATE, loanId,
                JsonPath.from(loanDetails).get("netDisbursalAmount").toString());
        LoanStatusChecker.verifyLoanIsActive(status);

        final Integer penaltyId = ChargesHelper.createCharges(this.requestSpec, this.responseSpec,
                ChargesHelper.getLoanSpecifiedDueDateJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, PENALTY_AMOUNT, true));
        this.loanTransactionHelper.addChargesForLoan(loanId,
                LoanTransactionHelper.getSpecifiedDueDateChargesForLoanAsJSON(String.valueOf(penaltyId), PENALTY_DUE_DATE,
                        PENALTY_AMOUNT));
        return loanId;
    }

    private Integer createLoanProduct(final Account... accounts) {
        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal("30000").withNumberOfRepayments("6")
                .withRepaymentAfterEvery("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("0")
                .withInterestRateFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments().withInterestTypeAsFlat()
                .withAccountingRulePeriodicAccrual(accounts).build(null);
        return this.loanTransactionHelper.getLoanProductId(loanProductJSON);
    }

    private Integer applyForLoanApplication(final Integer clientId, final Integer loanProductId) {
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("30000").withLoanTermFrequency("6")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("6").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withInterestRatePerPeriod("0").withInterestTypeAsFlatBalance()
                .withAmortizationTypeAsEqualInstallments().withInterestCalculationPeriodTypeSameAsRepaymentPeriod()
                .withExpectedDisbursementDate(DISBURSEMENT_DATE).withSubmittedOnDate(APPROVAL_DATE)
                .build(clientId.toString(), loanProductId.toString(), null);
        return this.loanTransactionHelper.getLoanId(loanApplicationJSON);
    }

    @SuppressWarnings("unchecked")
    private Integer firstPenaltyChargeId(final Integer loanId) {
        final ArrayList<HashMap<String, Object>> charges = this.loanTransactionHelper.getLoanCharges(loanId);
        for (final HashMap<String, Object> charge : charges) {
            if (Boolean.TRUE.equals(charge.get("penalty"))) {
                return (Integer) charge.get("id");
            }
        }
        throw new IllegalStateException("the fixture did not attach a penalty to loan " + loanId);
    }

    @SuppressWarnings("unchecked")
    private HashMap<String, Object> loanCharge(final Integer loanId, final Integer loanChargeId) {
        return Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/loans/" + loanId + "/charges/" + loanChargeId + "?" + Utils.TENANT_IDENTIFIER, "");
    }

    @SuppressWarnings("unchecked")
    private List<HashMap<String, Object>> loanTransactions(final Integer loanId) {
        return Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/loans/" + loanId + "?associations=transactions&" + Utils.TENANT_IDENTIFIER, "transactions");
    }

    private String typeCode(final HashMap<String, Object> transaction) {
        final Object type = transaction.get("type");
        if (type instanceof HashMap) {
            final Object code = ((HashMap<?, ?>) type).get("code");
            return code == null ? null : code.toString().replace("loanTransactionType.", "");
        }
        return null;
    }

    private Float asFloat(final Object value) {
        return value == null ? 0.0f : Float.parseFloat(value.toString());
    }
}

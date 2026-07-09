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
import org.apache.fineract.client.models.GlobalConfigurationPropertyData;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.PaymentTypeHelper;
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
 * Covers CGLT-532 / CGLT-562: editing the insurance payment captured at disbursement as "Repayment (at time of
 * disbursement)" when the original charge falls in a CLOSED accounting period. The system must auto-derive a
 * correction date (latest closure + 1 day) so the correcting GL entries post into the open period instead of
 * blocking the edit — mirroring the recovery-payment correction flow (CGLT-530). See
 * {@code LoanWithWaiveInterestAndWriteOffIntegrationTest#closedPeriodRecoveryCorrectionsAreAutoDerivedWithoutManualCorrectionDateInput}.
 */
public class LoanDisbursementInsuranceChargeCorrectionDateIntegrationTest {

    private static final String DATE_OF_JOINING = "01 January 2010";
    private static final String SUBMISSION_DATE = "01 December 2010";
    private static final String APPROVAL_DATE = "15 December 2010";
    private static final String DISBURSEMENT_DATE = "01 January 2011";
    private static final String CLOSURE_DATE = "31 January 2011";
    private static final List<Integer> EXPECTED_CLOSED_ACCOUNTING_DATE = List.of(2011, 1, 31);
    private static final List<Integer> EXPECTED_CORRECTION_DATE = List.of(2011, 2, 1);
    private static final String INSURANCE_AMOUNT = "100";
    private static final String CLOSED_PERIOD_CORRECTIONS_CONFIG = "corrections-in-closed-period";
    private static final String CLOSED_PERIOD_NOT_ALLOWED_ERROR = "error.msg.loan.transaction.closed.period.corrections.not.allowed";

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private LoanTransactionHelper loanTransactionHelper;
    private AccountHelper accountHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, this.responseSpec);
        this.accountHelper = new AccountHelper(this.requestSpec, this.responseSpec);
    }

    @Test
    public void closedPeriodInsuranceZeroingAutoDerivesCorrectionDate() {
        final Integer loanId = createDisbursedLoanWithInsuranceCharge();
        final Integer loanChargeId = firstLoanChargeId(loanId);
        final Integer repaymentAtDisbursementId = repaymentAtDisbursementTransactionId(loanId);

        final GlobalConfigurationPropertyData config = GlobalConfigurationHelper.getGlobalConfigurationByName(this.requestSpec,
                this.responseSpec, CLOSED_PERIOD_CORRECTIONS_CONFIG);
        final boolean originallyEnabled = Boolean.TRUE.equals(config.getEnabled());
        Integer glClosureId = null;
        try {
            if (!originallyEnabled) {
                GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(this.requestSpec, this.responseSpec, config.getId(),
                        true);
            }
            glClosureId = createGlClosure(1, CLOSURE_DATE);

            // The edit screen reads the closed-period correction window from the transaction template.
            final HashMap template = (HashMap) Utils.performServerGet(this.requestSpec, this.responseSpec,
                    "/fineract-provider/api/v1/loans/" + loanId + "/transactions/" + repaymentAtDisbursementId + "?template=true&"
                            + Utils.TENANT_IDENTIFIER,
                    "");
            Assertions.assertEquals(Boolean.TRUE, template.get("correctionAllowed"));
            Assertions.assertEquals(Boolean.FALSE, template.get("correctionDateRequired"));
            Assertions.assertEquals(EXPECTED_CLOSED_ACCOUNTING_DATE, template.get("latestClosedAccountingDate"));
            Assertions.assertEquals(EXPECTED_CORRECTION_DATE, template.get("earliestCorrectionDate"));

            // Zero the insurance amount on a charge booked in the now-closed period, without supplying a correction date.
            final HashMap response = this.loanTransactionHelper.adjustDisbursementCharge(loanId, loanChargeId,
                    adjustPayload("0", DISBURSEMENT_DATE, null, null));
            final HashMap changes = (HashMap) response.get("changes");
            Assertions.assertNotNull(changes, "adjustment changes should be returned");
            Assertions.assertEquals("2011-02-01", changes.get("correctionDate"),
                    "correction date should be auto-derived as latest closure + 1 day");

            // The charge amount is now zeroed while the transaction history is preserved.
            final HashMap loanCharge = this.loanTransactionHelper.getLoanCharge(loanId, loanChargeId);
            Assertions.assertEquals(0.0f, Float.valueOf(String.valueOf(loanCharge.get("amount"))));
        } finally {
            restore(glClosureId, originallyEnabled, config.getId());
        }
    }

    @Test
    public void closedPeriodInsurancePaymentTypeChangeAutoDerivesCorrectionDate() {
        final Integer loanId = createDisbursedLoanWithInsuranceCharge();
        final Integer loanChargeId = firstLoanChargeId(loanId);

        final Integer mpesaPaymentTypeId = PaymentTypeHelper.createPaymentType(this.requestSpec, this.responseSpec,
                "Mpesa-" + Utils.randomNameGenerator("PT_", 4), "Insurance payment type", Boolean.FALSE, 1);

        final GlobalConfigurationPropertyData config = GlobalConfigurationHelper.getGlobalConfigurationByName(this.requestSpec,
                this.responseSpec, CLOSED_PERIOD_CORRECTIONS_CONFIG);
        final boolean originallyEnabled = Boolean.TRUE.equals(config.getEnabled());
        Integer glClosureId = null;
        try {
            if (!originallyEnabled) {
                GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(this.requestSpec, this.responseSpec, config.getId(),
                        true);
            }
            glClosureId = createGlClosure(1, CLOSURE_DATE);

            // Move the insurance payment to a different payment type/account in the closed period.
            final HashMap response = this.loanTransactionHelper.adjustDisbursementCharge(loanId, loanChargeId,
                    adjustPayload(INSURANCE_AMOUNT, DISBURSEMENT_DATE, mpesaPaymentTypeId, null));
            final HashMap changes = (HashMap) response.get("changes");
            Assertions.assertNotNull(changes, "adjustment changes should be returned");
            Assertions.assertEquals("2011-02-01", changes.get("correctionDate"),
                    "correction date should be auto-derived as latest closure + 1 day");
            Assertions.assertEquals(mpesaPaymentTypeId, changes.get("paymentTypeId"));
        } finally {
            restore(glClosureId, originallyEnabled, config.getId());
        }
    }

    @Test
    public void closedPeriodInsuranceEditBlockedWhenCorrectionsDisabled() {
        final Integer loanId = createDisbursedLoanWithInsuranceCharge();
        final Integer loanChargeId = firstLoanChargeId(loanId);

        final GlobalConfigurationPropertyData config = GlobalConfigurationHelper.getGlobalConfigurationByName(this.requestSpec,
                this.responseSpec, CLOSED_PERIOD_CORRECTIONS_CONFIG);
        final boolean originallyEnabled = Boolean.TRUE.equals(config.getEnabled());
        Integer glClosureId = null;
        try {
            if (originallyEnabled) {
                GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(this.requestSpec, this.responseSpec, config.getId(),
                        false);
            }
            glClosureId = createGlClosure(1, CLOSURE_DATE);

            final LoanTransactionHelper validationErrorHelper = new LoanTransactionHelper(this.requestSpec,
                    new ResponseSpecBuilder().expectStatusCode(403).build());
            final HashMap response = validationErrorHelper.adjustDisbursementCharge(loanId, loanChargeId,
                    adjustPayload("0", DISBURSEMENT_DATE, null, null));
            final ArrayList<HashMap> errors = (ArrayList<HashMap>) response.get("errors");
            Assertions.assertEquals(CLOSED_PERIOD_NOT_ALLOWED_ERROR, errors.get(0).get("userMessageGlobalisationCode"));
        } finally {
            restore(glClosureId, originallyEnabled, config.getId());
        }
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private Integer createDisbursedLoanWithInsuranceCharge() {
        final Integer clientId = ClientHelper.createClient(this.requestSpec, this.responseSpec, DATE_OF_JOINING);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientId);

        final Account assetAccount = this.accountHelper.createAssetAccount();
        final Account incomeAccount = this.accountHelper.createIncomeAccount();
        final Account expenseAccount = this.accountHelper.createExpenseAccount();
        final Account overpaymentAccount = this.accountHelper.createLiabilityAccount();

        final Integer insuranceChargeId = ChargesHelper.createCharges(this.requestSpec, this.responseSpec,
                ChargesHelper.getLoanDisbursementJSON(ChargesHelper.CHARGE_CALCULATION_TYPE_FLAT, INSURANCE_AMOUNT));

        final Integer loanProductId = createCashBasedLoanProduct(assetAccount, incomeAccount, expenseAccount, overpaymentAccount);

        final List<HashMap> charges = new ArrayList<>();
        final HashMap<String, String> charge = new HashMap<>();
        charge.put("chargeId", insuranceChargeId.toString());
        charge.put("amount", INSURANCE_AMOUNT);
        charges.add(charge);

        final Integer loanId = applyForLoanApplication(clientId, loanProductId, charges);
        LoanStatusChecker.getStatusOfLoan(this.requestSpec, this.responseSpec, loanId);

        this.loanTransactionHelper.approveLoan(APPROVAL_DATE, loanId);
        final String loanDetails = this.loanTransactionHelper.getLoanDetails(this.requestSpec, this.responseSpec, loanId);
        final HashMap status = this.loanTransactionHelper.disburseLoan(DISBURSEMENT_DATE, loanId,
                JsonPath.from(loanDetails).get("netDisbursalAmount").toString());
        LoanStatusChecker.verifyLoanIsActive(status);
        return loanId;
    }

    private Integer createCashBasedLoanProduct(final Account... accounts) {
        final String loanProductJSON = new LoanProductTestBuilder().withPrincipal("10000").withNumberOfRepayments("6")
                .withRepaymentAfterEvery("1").withRepaymentTypeAsMonth().withinterestRatePerPeriod("1")
                .withInterestRateFrequencyTypeAsMonths().withAmortizationTypeAsEqualInstallments().withInterestTypeAsDecliningBalance()
                .withAccountingRuleAsCashBased(accounts).build(null);
        return this.loanTransactionHelper.getLoanProductId(loanProductJSON);
    }

    private Integer applyForLoanApplication(final Integer clientId, final Integer loanProductId, final List<HashMap> charges) {
        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal("10000").withLoanTermFrequency("6")
                .withLoanTermFrequencyAsMonths().withNumberOfRepayments("6").withRepaymentEveryAfter("1")
                .withRepaymentFrequencyTypeAsMonths().withInterestRatePerPeriod("1").withInterestTypeAsDecliningBalance()
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod().withAmortizationTypeAsEqualInstallments()
                .withExpectedDisbursementDate(DISBURSEMENT_DATE).withSubmittedOnDate(SUBMISSION_DATE).withCharges(charges)
                .build(clientId.toString(), loanProductId.toString(), null);
        return this.loanTransactionHelper.getLoanId(loanApplicationJSON);
    }

    private Integer firstLoanChargeId(final Integer loanId) {
        final ArrayList<HashMap> loanCharges = this.loanTransactionHelper.getLoanCharges(loanId);
        Assertions.assertFalse(loanCharges.isEmpty(), "loan should have the insurance charge");
        return (Integer) loanCharges.get(0).get("id");
    }

    @SuppressWarnings("unchecked")
    private Integer repaymentAtDisbursementTransactionId(final Integer loanId) {
        final ArrayList<HashMap> transactions = this.loanTransactionHelper.getLoanTransactions(this.requestSpec, this.responseSpec, loanId);
        for (final HashMap transaction : transactions) {
            final HashMap type = (HashMap) transaction.get("type");
            if (type != null && Boolean.TRUE.equals(type.get("repaymentAtDisbursement"))) {
                return (Integer) transaction.get("id");
            }
        }
        Assertions.fail("No repayment-at-disbursement transaction found for loan " + loanId);
        return null;
    }

    private String adjustPayload(final String amount, final String transactionDate, final Integer paymentTypeId,
            final String correctionDate) {
        final HashMap<String, Object> payload = new HashMap<>();
        payload.put("amount", amount);
        payload.put("transactionDate", transactionDate);
        payload.put("notes", "Correcting wrongly captured insurance at disbursement");
        payload.put("locale", "en");
        payload.put("dateFormat", "dd MMMM yyyy");
        if (paymentTypeId != null) {
            payload.put("paymentTypeId", paymentTypeId);
        }
        if (correctionDate != null) {
            payload.put("correctionDate", correctionDate);
        }
        return new Gson().toJson(payload);
    }

    private Integer createGlClosure(final Integer officeId, final String closingDate) {
        final HashMap<String, Object> request = new HashMap<>();
        request.put("officeId", officeId);
        request.put("closingDate", closingDate);
        request.put("comments", "Test GL closure for insurance correction handling");
        request.put("locale", "en");
        request.put("dateFormat", "dd MMMM yyyy");
        return Utils.performServerPost(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/glclosures?" + Utils.TENANT_IDENTIFIER, new Gson().toJson(request), "resourceId");
    }

    private void deleteGlClosure(final Integer glClosureId) {
        Utils.performServerDelete(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/glclosures/" + glClosureId + "?" + Utils.TENANT_IDENTIFIER, "resourceId");
    }

    private void restore(final Integer glClosureId, final boolean originallyEnabled, final Long configId) {
        if (glClosureId != null) {
            deleteGlClosure(glClosureId);
        }
        final GlobalConfigurationPropertyData config = GlobalConfigurationHelper.getGlobalConfigurationByName(this.requestSpec,
                this.responseSpec, CLOSED_PERIOD_CORRECTIONS_CONFIG);
        if (!Boolean.valueOf(originallyEnabled).equals(config.getEnabled())) {
            GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(this.requestSpec, this.responseSpec, configId,
                    originallyEnabled);
        }
    }
}

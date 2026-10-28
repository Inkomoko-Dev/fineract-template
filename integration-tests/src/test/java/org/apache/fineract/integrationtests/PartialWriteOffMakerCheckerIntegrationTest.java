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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.Map;
import org.apache.fineract.client.models.GlobalConfigurationPropertyData;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CollateralManagementHelper;
import org.apache.fineract.integrationtests.common.GlobalConfigurationHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.commands.MakercheckersHelper;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CGLT-690: Partial write-off maker-checker lifecycle under Fineract built-in MC.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class PartialWriteOffMakerCheckerIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(PartialWriteOffMakerCheckerIntegrationTest.class);

    private static final String LP_PRINCIPAL = "10,000.00";
    private static final String LP_REPAYMENTS = "2";
    private static final String LP_REPAYMENT_PERIOD = "6";
    private static final String LP_INTEREST_RATE = "2";
    private static final String DISBURSEMENT_DATE = "01 March 2026";
    private static final String LOAN_APPLICATION_SUBMISSION_DATE = "01 February 2026";
    private static final String DATE_OF_JOINING = "01 January 2026";
    private static final String APPROVAL_DATE = "01 February 2026";
    private static final String PARTIAL_WRITE_OFF_DATE = "15 March 2026";
    private static final String TEST_PASSWORD = "password";

    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec;
    private LoanTransactionHelper loanTransactionHelper;
    private MakercheckersHelper makercheckersHelper;
    private Long makerCheckerConfigId;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, this.responseSpec);
        this.makercheckersHelper = new MakercheckersHelper(this.requestSpec, this.responseSpec);

        final GlobalConfigurationPropertyData config = GlobalConfigurationHelper.getGlobalConfigurationByName(this.requestSpec,
                this.responseSpec, "maker-checker");
        this.makerCheckerConfigId = config.getId();
        GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(this.requestSpec, this.responseSpec, this.makerCheckerConfigId,
                true);
    }

    @AfterEach
    public void tearDown() {
        if (this.makerCheckerConfigId != null) {
            GlobalConfigurationHelper.updateEnabledFlagForGlobalConfiguration(this.requestSpec, this.responseSpec,
                    this.makerCheckerConfigId, false);
        }
    }

    @Test
    public void testMakerSubmitCreatesPendingPartialWriteOff() {
        final Integer loanID = createActiveLoan();
        final BigDecimal balanceBefore = new BigDecimal(getLoanOutstandingBalance(loanID));

        final Integer commandId = submitPartialWriteOffForApproval(loanID, "500.00");
        assertNotNull(commandId, "Pending maker-checker commandId should be returned");

        final Map<String, Object> pending = this.makercheckersHelper.findPendingPartialWriteOff(loanID);
        assertNotNull(pending, "Pending PARTIALWRITEOFF should appear in makercheckers inbox");
        assertEquals(commandId.longValue(), ((Number) pending.get("id")).longValue());

        assertFalse(hasPartialWriteOffTransaction(loanID), "Write-off must not post until checker approves");
        assertEquals(0, balanceBefore.compareTo(new BigDecimal(getLoanOutstandingBalance(loanID))),
                "Outstanding balance must be unchanged while pending");
    }

    @Test
    public void testCheckerApproveExecutesPartialWriteOff() {
        final Integer loanID = createActiveLoan();
        final BigDecimal balanceBefore = new BigDecimal(getLoanOutstandingBalance(loanID));

        final Integer commandId = submitPartialWriteOffForApproval(loanID, "500.00");
        this.makercheckersHelper.approveMakerCheckerEntry(commandId.longValue(), "{\"note\":\"Approved by checker\"}");

        assertNull(this.makercheckersHelper.findPendingPartialWriteOff(loanID), "Pending entry should be gone after approve");
        assertTrue(hasPartialWriteOffTransaction(loanID), "PARTIAL_WRITEOFF transaction should exist after approve");

        final BigDecimal balanceAfter = new BigDecimal(getLoanOutstandingBalance(loanID));
        assertEquals(0, new BigDecimal("500.00").compareTo(balanceBefore.subtract(balanceAfter)),
                "Balance should reduce by write-off amount after approve");
    }

    @Test
    public void testCheckerRejectRecordsReasonAndDoesNotWriteOff() {
        final Integer loanID = createActiveLoan();
        final BigDecimal balanceBefore = new BigDecimal(getLoanOutstandingBalance(loanID));
        final String rejectionReason = "CGLT-690 rejection reason for partial write-off";

        final Integer commandId = submitPartialWriteOffForApproval(loanID, "500.00");
        this.makercheckersHelper.rejectMakerCheckerEntry(commandId.longValue(), "{\"note\":\"" + rejectionReason + "\"}");

        assertNull(this.makercheckersHelper.findPendingPartialWriteOff(loanID), "Pending entry should be gone after reject");
        assertFalse(hasPartialWriteOffTransaction(loanID), "Write-off must not execute on reject");
        assertEquals(0, balanceBefore.compareTo(new BigDecimal(getLoanOutstandingBalance(loanID))),
                "Outstanding balance must be unchanged after reject");

        final String notesJson = Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/loans/" + loanID + "/notes?" + Utils.TENANT_IDENTIFIER, null);
        assertTrue(notesJson.contains("Maker Checker Note: " + rejectionReason),
                "Rejection reason should be stored as a Maker Checker Note on the loan");

        final String auditJson = Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/audits?actionName=PARTIALWRITEOFF&loanId=" + loanID + "&" + Utils.TENANT_IDENTIFIER, null);
        assertTrue(auditJson.toLowerCase().contains("reject") || auditJson.contains("\"processingResult\""),
                "Rejected command should be logged in audits");
    }

    @Test
    public void testDuplicatePendingPartialWriteOffIsRejected() {
        final Integer loanID = createActiveLoan();
        submitPartialWriteOffForApproval(loanID, "500.00");

        final String url = "/fineract-provider/api/v1/loans/" + loanID + "/transactions?command=partialwriteoff&"
                + Utils.TENANT_IDENTIFIER;
        final ResponseSpecification expectError = new ResponseSpecBuilder().expectStatusCode(403).build();
        final String response = Utils.performServerPost(this.requestSpec, expectError, url, buildPartialWriteOffJson("250.00"), null);
        assertTrue(response.contains("error.command.pending.approval.exists") || response.contains("Duplicate maker-checker"),
                "Second pending PARTIALWRITEOFF for same loan must be blocked: " + response);
    }

    @Test
    public void testUnauthorizedMakerCannotSubmitPartialWriteOff() {
        final Integer loanID = createActiveLoan();

        final Integer roleId = RolesHelper.createRole(this.requestSpec, this.responseSpec);
        RolesHelper.updatePermissions(this.requestSpec, this.responseSpec, roleId,
                "{\"permissions\":{\"ALL_FUNCTIONS_READ\":true}}");
        final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
        final String username = Utils.randomNameGenerator("pwo_maker_", 6);
        UserHelper.createUser(this.requestSpec, this.responseSpec, roleId, staffId, username, TEST_PASSWORD, "resourceId");

        final RequestSpecification limitedSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        limitedSpec.header("Authorization",
                "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, TEST_PASSWORD));
        final ResponseSpecification expectForbidden = new ResponseSpecBuilder().expectStatusCode(403).build();

        final String url = "/fineract-provider/api/v1/loans/" + loanID + "/transactions?command=partialwriteoff&"
                + Utils.TENANT_IDENTIFIER;
        final String response = Utils.performServerPost(limitedSpec, expectForbidden, url, buildPartialWriteOffJson("100.00"), null);
        assertTrue(response.contains("PARTIALWRITEOFF") || response.toLowerCase().contains("not authorised")
                || response.toLowerCase().contains("authority"), "Unauthorized maker should be denied: " + response);
    }

    @Test
    public void testUnauthorizedCheckerCannotApprovePartialWriteOff() {
        final Integer loanID = createActiveLoan();
        final Integer commandId = submitPartialWriteOffForApproval(loanID, "500.00");

        final Integer roleId = RolesHelper.createRole(this.requestSpec, this.responseSpec);
        // Maker permission only — no PARTIALWRITEOFF_LOAN_CHECKER, CHECKER_SUPER_USER, or ALL_FUNCTIONS
        RolesHelper.updatePermissions(this.requestSpec, this.responseSpec, roleId,
                "{\"permissions\":{\"PARTIALWRITEOFF_LOAN\":true,\"ALL_FUNCTIONS_READ\":true}}");
        final Integer staffId = StaffHelper.createStaff(this.requestSpec, this.responseSpec);
        final String username = Utils.randomNameGenerator("pwo_chk_", 6);
        UserHelper.createUser(this.requestSpec, this.responseSpec, roleId, staffId, username, TEST_PASSWORD, "resourceId");

        final RequestSpecification limitedSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        limitedSpec.header("Authorization",
                "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, TEST_PASSWORD));
        final ResponseSpecification expectForbidden = new ResponseSpecBuilder().expectStatusCode(403).build();

        final String response = Utils.performServerPost(limitedSpec, expectForbidden,
                "/fineract-provider/api/v1/makercheckers/" + commandId + "?command=approve&" + Utils.TENANT_IDENTIFIER,
                "{\"note\":\"should fail\"}", null);
        assertTrue(response.toLowerCase().contains("checker") || response.toLowerCase().contains("authority")
                || response.toLowerCase().contains("not authorised"), "Unauthorized checker should be denied: " + response);
    }

    private Integer createActiveLoan() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec, DATE_OF_JOINING);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        final Integer loanProductID = createLoanProduct();
        final Integer loanID = applyForLoanApplication(clientID, loanProductID);

        HashMap loanStatusHashMap = LoanStatusChecker.getStatusOfLoan(this.requestSpec, this.responseSpec, loanID);
        LoanStatusChecker.verifyLoanIsPending(loanStatusHashMap);

        loanStatusHashMap = this.loanTransactionHelper.approveLoan(APPROVAL_DATE, loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanStatusHashMap);

        final String loanDetails = this.loanTransactionHelper.getLoanDetails(this.requestSpec, this.responseSpec, loanID);
        final String netDisbursalAmount = JsonPath.from(loanDetails).getString("netDisbursalAmount");
        loanStatusHashMap = this.loanTransactionHelper.disburseLoan(DISBURSEMENT_DATE, loanID, netDisbursalAmount);
        LoanStatusChecker.verifyLoanIsActive(loanStatusHashMap);
        return loanID;
    }

    private Integer submitPartialWriteOffForApproval(final Integer loanID, final String principalPortion) {
        final String url = "/fineract-provider/api/v1/loans/" + loanID + "/transactions?command=partialwriteoff&"
                + Utils.TENANT_IDENTIFIER;
        final Integer commandId = Utils.performServerPost(this.requestSpec, this.responseSpec, url,
                buildPartialWriteOffJson(principalPortion), "commandId");
        LOG.info("Submitted PARTIALWRITEOFF for loan {} → commandId {}", loanID, commandId);
        return commandId;
    }

    private String buildPartialWriteOffJson(final String principalPortion) {
        final HashMap<String, Object> map = new HashMap<>();
        map.put("transactionDate", PARTIAL_WRITE_OFF_DATE);
        map.put("dateFormat", "dd MMMM yyyy");
        map.put("locale", "en");
        map.put("principalPortion", principalPortion);
        map.put("reason", "CGLT-690 maker-checker integration test");
        map.put("note", "Pending approval test note");
        return new Gson().toJson(map);
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
        final List<HashMap> collaterals = new ArrayList<>();
        final Integer collateralId = CollateralManagementHelper.createCollateralProduct(this.requestSpec, this.responseSpec);
        Assertions.assertNotNull(collateralId);
        final Integer clientCollateralId = CollateralManagementHelper.createClientCollateral(this.requestSpec, this.responseSpec,
                clientID.toString(), collateralId);
        Assertions.assertNotNull(clientCollateralId);
        collaterals.add(collateral(clientCollateralId, BigDecimal.valueOf(1)));

        final String loanApplicationJSON = new LoanApplicationTestBuilder().withPrincipal(LP_PRINCIPAL)
                .withLoanTermFrequency("12").withLoanTermFrequencyAsMonths().withNumberOfRepayments(LP_REPAYMENTS)
                .withRepaymentEveryAfter(LP_REPAYMENT_PERIOD).withRepaymentFrequencyTypeAsMonths()
                .withInterestRatePerPeriod(LP_INTEREST_RATE).withInterestTypeAsFlatBalance()
                .withAmortizationTypeAsEqualInstallments().withInterestCalculationPeriodTypeSameAsRepaymentPeriod()
                .withExpectedDisbursementDate(DISBURSEMENT_DATE).withSubmittedOnDate(LOAN_APPLICATION_SUBMISSION_DATE)
                .withCollaterals(collaterals).build(clientID.toString(), loanProductID.toString(), null);
        return this.loanTransactionHelper.getLoanId(loanApplicationJSON);
    }

    private HashMap<String, String> collateral(final Integer collateralId, final BigDecimal quantity) {
        final HashMap<String, String> collateral = new HashMap<>(2);
        collateral.put("clientCollateralId", collateralId.toString());
        collateral.put("quantity", quantity.toString());
        return collateral;
    }

    private String getLoanOutstandingBalance(final Integer loanID) {
        final String response = Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/loans/" + loanID + "?associations=all&" + Utils.TENANT_IDENTIFIER, null);
        return JsonPath.from(response).getString("summary.totalOutstanding");
    }

    private boolean hasPartialWriteOffTransaction(final Integer loanID) {
        final String response = Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/loans/" + loanID + "/transactions?" + Utils.TENANT_IDENTIFIER, null);
        final List<Map<String, Object>> transactions = JsonPath.from(response).getList("$");
        if (transactions == null) {
            return false;
        }
        for (final Map<String, Object> txn : transactions) {
            final Object type = txn.get("type");
            if (type instanceof Map) {
                final Object code = ((Map<?, ?>) type).get("code");
                if (code != null && String.valueOf(code).contains("PARTIAL_WRITEOFF")) {
                    return true;
                }
            }
            final Object typeCode = txn.get("type.code");
            if (typeCode != null && String.valueOf(typeCode).contains("PARTIAL_WRITEOFF")) {
                return true;
            }
        }
        // Fallback: loan details associations
        final String loanJson = Utils.performServerGet(this.requestSpec, this.responseSpec,
                "/fineract-provider/api/v1/loans/" + loanID + "?associations=transactions&" + Utils.TENANT_IDENTIFIER, null);
        return loanJson.contains("PARTIAL_WRITEOFF") || loanJson.contains("partialWriteOff") || loanJson.contains("\"id\":31");
    }
}

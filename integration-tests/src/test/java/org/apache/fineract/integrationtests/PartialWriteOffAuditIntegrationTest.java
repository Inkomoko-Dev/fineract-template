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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.math.BigDecimal;
import java.util.HashMap;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.loans.LoanApplicationTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanProductTestBuilder;
import org.apache.fineract.integrationtests.common.loans.LoanStatusChecker;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration test for Partial Write-Off Audit Trail Validation
 * CGLT-680: Verifies that audit records correctly capture before/after balances
 * for mixed-component write-offs (Principal + Interest + Fees + Penalties)
 */
@SuppressWarnings({ "rawtypes" })
public class PartialWriteOffAuditIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(PartialWriteOffAuditIntegrationTest.class);
    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;

    private static final String LP_PRINCIPAL = "10,000.00";
    private static final String LP_REPAYMENTS = "2";
    private static final String LP_REPAYMENT_PERIOD = "6";
    private static final String LP_INTEREST_RATE = "2";
    private static final String DISBURSEMENT_DATE = "01 March 2026";
    private static final String LOAN_APPLICATION_SUBMISSION_DATE = "01 February 2026";
    private static final String DATE_OF_JOINING = "01 January 2026";

    private LoanTransactionHelper loanTransactionHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, this.responseSpec);
    }

    @Test
    public void testPartialWriteOffAuditTrailForMixedComponents() {
        // CREATE CLIENT
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec, DATE_OF_JOINING);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        // CREATE LOAN PRODUCT
        final Integer loanProductID = createLoanProduct();
        LoanProductTestBuilder.verifyLoanProductCreated(loanProductID);

        // CREATE LOAN APPLICATION
        final Integer loanID = createLoanApplication(clientID, loanProductID);
        LoanStatusChecker.verifyLoanIsPending(loanID);

        // APPROVE LOAN
        LoanStatusChecker.approveLoan(loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanID);

        // DISBURSE LOAN
        LoanStatusChecker.disburseLoan(loanID, DISBURSEMENT_DATE);
        LoanStatusChecker.verifyLoanIsActive(loanID);

        // GET LOAN BALANCE BEFORE WRITE-OFF
        final String loanBalanceBefore = getLoanOutstandingBalance(loanID);
        assertNotNull(loanBalanceBefore, "Loan balance before write-off should not be null");
        LOG.info("Loan balance before partial write-off: {}", loanBalanceBefore);

        // PERFORM MIXED-COMPONENT PARTIAL WRITE-OFF
        final String partialWriteOffDate = "15 March 2026";
        final HashMap<String, Object> partialWriteOffMap = new HashMap<>();
        partialWriteOffMap.put("transactionDate", partialWriteOffDate);
        partialWriteOffMap.put("principalPortion", "500.00");
        partialWriteOffMap.put("interestPortion", "100.00");
        partialWriteOffMap.put("feeChargesPortion", "50.00");
        partialWriteOffMap.put("penaltyChargesPortion", "25.00");
        partialWriteOffMap.put("reason", "Test mixed-component write-off for audit validation");
        partialWriteOffMap.put("note", "Integration test for CGLT-680 audit trail validation");

        final String partialWriteOffJson = new Gson().toJson(partialWriteOffMap);
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, new ResponseSpecBuilder().build());
        this.loanTransactionHelper.partialWriteOffLoan(loanID, partialWriteOffJson);

        // VERIFY LOAN REMAINS ACTIVE
        LoanStatusChecker.verifyLoanIsActive(loanID);

        // GET LOAN BALANCE AFTER WRITE-OFF
        final String loanBalanceAfter = getLoanOutstandingBalance(loanID);
        assertNotNull(loanBalanceAfter, "Loan balance after write-off should not be null");
        LOG.info("Loan balance after partial write-off: {}", loanBalanceAfter);

        // VERIFY BALANCE REDUCTION
        final BigDecimal beforeBalance = new BigDecimal(loanBalanceBefore);
        final BigDecimal afterBalance = new BigDecimal(loanBalanceAfter);
        final BigDecimal expectedReduction = new BigDecimal("675.00"); // 500 + 100 + 50 + 25
        final BigDecimal actualReduction = beforeBalance.subtract(afterBalance);
        
        assertEquals(expectedReduction, actualReduction, 
            "Balance reduction should match total write-off amount");

        // GET AUDIT RECORD
        final String auditData = getPartialWriteOffAuditData(loanID);
        assertNotNull(auditData, "Audit data should not be null");
        
        final JsonPath auditJson = new JsonPath(auditData);
        
        // VERIFY AUDIT CAPTURES COMPONENT BREAKDOWN
        final String principalPortion = auditJson.getString("[0].principalPortion");
        final String interestPortion = auditJson.getString("[0].interestPortion");
        final String feeChargesPortion = auditJson.getString("[0].feeChargesPortion");
        final String penaltyChargesPortion = auditJson.getString("[0].penaltyChargesPortion");
        final String totalAmount = auditJson.getString("[0].totalAmount");
        final String loanBalanceBeforeAudit = auditJson.getString("[0].loanBalanceBefore");
        final String loanBalanceAfterAudit = auditJson.getString("[0].loanBalanceAfter");
        final String reason = auditJson.getString("[0].reason");

        assertNotNull(principalPortion, "Principal portion should be captured in audit");
        assertNotNull(interestPortion, "Interest portion should be captured in audit");
        assertNotNull(feeChargesPortion, "Fee charges portion should be captured in audit");
        assertNotNull(penaltyChargesPortion, "Penalty charges portion should be captured in audit");
        assertNotNull(totalAmount, "Total amount should be captured in audit");
        assertNotNull(loanBalanceBeforeAudit, "Loan balance before should be captured in audit");
        assertNotNull(loanBalanceAfterAudit, "Loan balance after should be captured in audit");
        assertNotNull(reason, "Reason should be captured in audit");

        // VERIFY AUDIT VALUES ARE CORRECT
        assertEquals("500.00", principalPortion, "Principal portion mismatch in audit");
        assertEquals("100.00", interestPortion, "Interest portion mismatch in audit");
        assertEquals("50.00", feeChargesPortion, "Fee charges portion mismatch in audit");
        assertEquals("25.00", penaltyChargesPortion, "Penalty charges portion mismatch in audit");
        assertEquals("675.00", totalAmount, "Total amount mismatch in audit");
        assertEquals(loanBalanceBefore, loanBalanceBeforeAudit, "Loan balance before mismatch in audit");
        assertEquals(loanBalanceAfter, loanBalanceAfterAudit, "Loan balance after mismatch in audit");
        assertEquals("Test mixed-component write-off for audit validation", reason, "Reason mismatch in audit");

        LOG.info("CGLT-680 Audit trail validation test passed successfully");
    }

    @Test
    public void testDuplicatePartialWriteOffPrevention() {
        // CREATE CLIENT
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec, DATE_OF_JOINING);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        // CREATE LOAN PRODUCT
        final Integer loanProductID = createLoanProduct();
        LoanProductTestBuilder.verifyLoanProductCreated(loanProductID);

        // CREATE LOAN APPLICATION
        final Integer loanID = createLoanApplication(clientID, loanProductID);
        LoanStatusChecker.verifyLoanIsPending(loanID);

        // APPROVE LOAN
        LoanStatusChecker.approveLoan(loanID);
        LoanStatusChecker.verifyLoanIsApproved(loanID);

        // DISBURSE LOAN
        LoanStatusChecker.disburseLoan(loanID, DISBURSEMENT_DATE);
        LoanStatusChecker.verifyLoanIsActive(loanID);

        // PERFORM FIRST PARTIAL WRITE-OFF
        final String partialWriteOffDate = "15 March 2026";
        final HashMap<String, Object> partialWriteOffMap = new HashMap<>();
        partialWriteOffMap.put("transactionDate", partialWriteOffDate);
        partialWriteOffMap.put("principalPortion", "500.00");
        partialWriteOffMap.put("reason", "First partial write-off");
        partialWriteOffMap.put("note", "Test duplicate prevention");

        final String partialWriteOffJson = new Gson().toJson(partialWriteOffMap);
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, new ResponseSpecBuilder().build());
        this.loanTransactionHelper.partialWriteOffLoan(loanID, partialWriteOffJson);

        // ATTEMPT SECOND PARTIAL WRITE-OFF ON SAME DAY (SHOULD FAIL)
        final HashMap<String, Object> duplicateWriteOffMap = new HashMap<>();
        duplicateWriteOffMap.put("transactionDate", partialWriteOffDate);
        duplicateWriteOffMap.put("principalPortion", "300.00");
        duplicateWriteOffMap.put("reason", "Second partial write-off on same day");
        duplicateWriteOffMap.put("note", "Should fail due to duplicate prevention");

        final String duplicateWriteOffJson = new Gson().toJson(duplicateWriteOffMap);
        final LoanTransactionHelper errorHelper = new LoanTransactionHelper(this.requestSpec, new ResponseSpecBuilder().build());
        
        try {
            errorHelper.partialWriteOffLoan(loanID, duplicateWriteOffJson);
            assertTrue(false, "Second partial write-off on same day should have failed");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("duplicate") || e.getMessage().contains("already exists"),
                "Error should indicate duplicate write-off prevention");
            LOG.info("CGLT-680 Duplicate prevention test passed successfully");
        }
    }

    private Integer createLoanProduct() {
        final String productJSON = new LoanProductTestBuilder()
                .withPrincipal(LP_PRINCIPAL)
                .withNumberOfRepayments(LP_REPAYMENTS)
                .withRepaymentPeriod(LP_REPAYMENT_PERIOD)
                .withRepaymentPeriodAsMonths()
                .withInterestRate(LP_INTEREST_RATE)
                .withInterestRateAsPerAnnum()
                .withInterestRateFrequencyTypeAsMonths()
                .withAmortizationTypeAsEqualPrincipalPayments()
                .withInterestTypeAsDecliningBalance()
                .withInterestCalculationPeriodTypeAsSameAsRepaymentPeriod()
                .build(null);
        return LoanProductTestBuilder.createLoanProduct(productJSON);
    }

    private Integer createLoanApplication(final Integer clientID, final Integer loanProductID) {
        final String loanApplicationJSON = new LoanApplicationTestBuilder()
                .withPrincipal(LP_PRINCIPAL)
                .withLoanTermFrequencyAsMonths()
                .withNumberOfRepayments(LP_REPAYMENTS)
                .withRepaymentEvery(LP_REPAYMENT_PERIOD)
                .withRepaymentPeriodAsMonths()
                .withInterestRate(LP_INTEREST_RATE)
                .withInterestRateAsPerAnnum()
                .withInterestRateFrequencyTypeAsMonths()
                .withAmortizationTypeAsEqualPrincipalPayments()
                .withInterestTypeAsDecliningBalance()
                .withInterestCalculationPeriodTypeAsSameAsRepaymentPeriod()
                .withExpectedDisbursementDate(DISBURSEMENT_DATE)
                .withSubmittedOnDate(LOAN_APPLICATION_SUBMISSION_DATE)
                .build(clientID.toString(), loanProductID.toString(), null);
        return LoanApplicationTestBuilder.createLoanApplication(loanApplicationJSON);
    }

    private String getLoanOutstandingBalance(final Integer loanID) {
        final String response = Utils.performServerGet("/loans/" + loanID, "", requestSpec, responseSpec);
        final JsonPath jsonPath = new JsonPath(response);
        return jsonPath.getString("summary.totalOutstanding");
    }

    private String getPartialWriteOffAuditData(final Integer loanID) {
        final String response = Utils.performServerGet("/loans/" + loanID + "/transactions", "", requestSpec, responseSpec);
        final JsonPath jsonPath = new JsonPath(response);
        
        // Filter for partial write-off transactions
        final String auditData = jsonPath.getString("$[?(@.type.code == 'PARTIAL_WRITEOFF')]");
        return auditData;
    }
}
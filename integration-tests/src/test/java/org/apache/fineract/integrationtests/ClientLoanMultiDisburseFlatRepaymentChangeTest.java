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

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * CGLT-641: changing the number of repayments on a flat-interest multi-disburse loan used to fail with
 * "Flat interest type is not allowed for multi disburse loan." This test creates such a loan and modifies the number
 * of repayments from 24 to 12, asserting the modify succeeds and the schedule is recalculated to 12 installments.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public class ClientLoanMultiDisburseFlatRepaymentChangeTest {

    private static final Logger LOG = LoggerFactory.getLogger(ClientLoanMultiDisburseFlatRepaymentChangeTest.class);

    private static final String PRINCIPAL = "12,000.00";
    private static final String DISBURSEMENT_DATE = "01 January 2021";

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private LoanTransactionHelper loanTransactionHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.loanTransactionHelper = new LoanTransactionHelper(this.requestSpec, this.responseSpec);
    }

    private Integer createFlatMultiDisburseProduct() {
        LOG.info("------------------------------CREATING FLAT MULTI-DISBURSE LOAN PRODUCT ----------------------------");
        final String loanProductJSON = new LoanProductTestBuilder() //
                .withPrincipal(PRINCIPAL) //
                .withNumberOfRepayments("24") //
                .withRepaymentAfterEvery("1") //
                .withRepaymentTypeAsMonth() //
                .withinterestRatePerPeriod("1") //
                .withInterestRateFrequencyTypeAsMonths() //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsFlat() //
                .withTranches(true) //
                .withInterestCalculationPeriodTypeAsRepaymentPeriod(true) //
                .withMaxTrancheCount("30") //
                .build(null);
        return this.loanTransactionHelper.getLoanProductId(loanProductJSON);
    }

    private HashMap createTrancheDetail(final String date, final String amount) {
        final HashMap detail = new HashMap();
        detail.put("expectedDisbursementDate", date);
        detail.put("principal", amount);
        return detail;
    }

    private List<HashMap> tranches() {
        final List<HashMap> tranches = new ArrayList<>();
        tranches.add(createTrancheDetail(DISBURSEMENT_DATE, "6000"));
        tranches.add(createTrancheDetail("01 February 2021", "6000"));
        return tranches;
    }

    private String loanApplicationJson(final Integer clientID, final Integer loanProductID, final String numberOfRepayments,
            final String loanTermFrequency) {
        return new LoanApplicationTestBuilder() //
                .withPrincipal(PRINCIPAL) //
                .withLoanTermFrequency(loanTermFrequency) //
                .withLoanTermFrequencyAsMonths() //
                .withNumberOfRepayments(numberOfRepayments) //
                .withRepaymentEveryAfter("1") //
                .withRepaymentFrequencyTypeAsMonths() //
                .withInterestRatePerPeriod("1") //
                .withAmortizationTypeAsEqualInstallments() //
                .withInterestTypeAsFlatBalance() //
                .withInterestCalculationPeriodTypeSameAsRepaymentPeriod() //
                .withExpectedDisbursementDate(DISBURSEMENT_DATE) //
                .withTranches(tranches()) //
                .withSubmittedOnDate(DISBURSEMENT_DATE) //
                .build(clientID.toString(), loanProductID.toString(), null);
    }

    private long repaymentPeriodCount(final Integer loanID) {
        final ArrayList<HashMap> loanSchedule = this.loanTransactionHelper.getLoanRepaymentSchedule(this.requestSpec, this.responseSpec,
                loanID);
        return loanSchedule.stream().filter(period -> period.get("period") != null).count();
    }

    @Test
    public void changeNumberOfRepaymentsOnFlatMultiDisburseLoanFrom24To12() {
        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        ClientHelper.verifyClientCreatedOnServer(this.requestSpec, this.responseSpec, clientID);

        final Integer loanProductID = createFlatMultiDisburseProduct();
        assertNotNull(loanProductID, "Flat multi-disburse loan product should be created (product-level validation relaxed)");

        // Apply for a pending loan with 24 repayments.
        final Integer loanID = this.loanTransactionHelper.getLoanId(loanApplicationJson(clientID, loanProductID, "24", "24"));
        assertNotNull(loanID);
        LoanStatusChecker.verifyLoanIsPending(LoanStatusChecker.getStatusOfLoan(this.requestSpec, this.responseSpec, loanID));
        assertEquals(24L, repaymentPeriodCount(loanID), "Loan should start with 24 repayment installments");

        // CGLT-641: modify the number of repayments to 12 — previously rejected with the flat/multi-disburse error.
        final Integer updatedLoanID = this.loanTransactionHelper.updateLoan(loanID, loanApplicationJson(clientID, loanProductID, "12", "12"));
        assertNotNull(updatedLoanID, "Modifying repayments on a flat multi-disburse loan should succeed (CGLT-641)");

        // Schedule must be recalculated to 12 installments.
        assertEquals(12L, repaymentPeriodCount(loanID), "Repayment schedule should be recalculated to 12 installments");
    }
}

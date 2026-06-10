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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.common.AccountingConstants;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.producttoaccountmapping.domain.PortfolioProductType;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMapping;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.monetary.domain.MonetaryCurrency;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.account.service.AccountTransfersWritePlatformService;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanAccountDomainService;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanDisbursementDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRelatedDetail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LoanWritePlatformServiceJpaRepositoryImplTest {

    private static final MonetaryCurrency KES = new MonetaryCurrency("KES", 2, 0);
    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 6, 8);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 6, 8))));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void refreshInsuranceDisbursementNetDisbursalAmountRepairsCorruptedSingleDisbursementPrincipal() {
        final LoanDisbursementDetails disbursementDetails = new LoanDisbursementDetails(DISBURSEMENT_DATE, DISBURSEMENT_DATE,
                new BigDecimal("4200.00"), new BigDecimal("4200.00"));
        final Loan loan = singleDisbursementLoan(new BigDecimal("5000.00"), disbursementDetails,
                disbursement(new BigDecimal("5000.00")), repaymentAtDisbursement(new BigDecimal("800.00"), false));

        LoanWritePlatformServiceJpaRepositoryImpl.refreshInsuranceDisbursementNetDisbursalAmount(loan, null);

        assertAmount("5000.00", disbursementDetails.principal());
        assertAmount("4200.00", disbursementDetails.getNetDisbursalAmount());
        assertAmount("4200.00", loan.getNetDisbursalAmount());
    }

    @Test
    void refreshInsuranceDisbursementNetDisbursalAmountUsesOnlyActiveRepaymentAtDisbursementTransactions() {
        final LoanDisbursementDetails disbursementDetails = new LoanDisbursementDetails(DISBURSEMENT_DATE, DISBURSEMENT_DATE,
                new BigDecimal("5000.00"), new BigDecimal("4600.00"));
        final Loan loan = singleDisbursementLoan(new BigDecimal("5000.00"), disbursementDetails,
                disbursement(new BigDecimal("5000.00")), repaymentAtDisbursement(new BigDecimal("400.00"), true),
                repaymentAtDisbursement(new BigDecimal("800.00"), false));

        LoanWritePlatformServiceJpaRepositoryImpl.refreshInsuranceDisbursementNetDisbursalAmount(loan, null);

        assertAmount("5000.00", disbursementDetails.principal());
        assertAmount("4200.00", disbursementDetails.getNetDisbursalAmount());
        assertAmount("4200.00", loan.getNetDisbursalAmount());
    }

    @Test
    void insurancePaymentEditRecomputesLoanStatusAfterReprocessingTransactions() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class,
                CALLS_REAL_METHODS);
        final LoanRepositoryWrapper loanRepositoryWrapper = mock(LoanRepositoryWrapper.class);
        final LoanAccountDomainService loanAccountDomainService = mock(LoanAccountDomainService.class);
        ReflectionTestUtils.setField(service, "loanRepositoryWrapper", loanRepositoryWrapper);
        ReflectionTestUtils.setField(service, "loanAccountDomainService", loanAccountDomainService);
        ReflectionTestUtils.setField(service, "loanTransactionRepository", mock(LoanTransactionRepository.class));
        ReflectionTestUtils.setField(service, "accountTransfersWritePlatformService", mock(AccountTransfersWritePlatformService.class));
        final Loan loan = mock(Loan.class);

        ReflectionTestUtils.invokeMethod(service, "recalculateLoanAfterInsurancePaymentEdit", loan, (LoanCharge) null);

        verify(loan).refreshFeeChargesDueAtDisbursement();
        verify(loan).reprocessTransactions();
        verify(loan).updateLoanSummarAndStatus();
        verify(loanRepositoryWrapper).saveAndFlush(loan);
        verify(loanAccountDomainService).recalculateAccruals(loan);
    }

    @Test
    void upwardInsuranceAdjustmentPostsCustomerBalanceToLoanPortfolioAndInsuranceIncome() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class,
                CALLS_REAL_METHODS);
        final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
        final ProductToGLAccountMappingRepository mappingRepository = mock(ProductToGLAccountMappingRepository.class);
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount insuranceIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = insuranceAdjustmentTransaction(1L, new BigDecimal("400.00"), false);
        final Map<String, Object> changes = new HashMap<>();

        ReflectionTestUtils.setField(service, "journalEntryRepository", journalEntryRepository);
        ReflectionTestUtils.setField(service, "productToGLAccountMappingRepository", mappingRepository);
        when(mappingRepository.findCoreProductToFinAccountMapping(11L, PortfolioProductType.LOAN.getValue(),
                AccountingConstants.CashAccountsForLoan.LOAN_PORTFOLIO.getValue()))
                .thenReturn(ProductToGLAccountMapping.createNew(loanPortfolioAccount, 11L,
                        PortfolioProductType.LOAN.getValue(),
                        AccountingConstants.CashAccountsForLoan.LOAN_PORTFOLIO.getValue()));

        ReflectionTestUtils.invokeMethod(service, "postInsuranceCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, BigDecimal.ZERO, new BigDecimal("400.00"), null, insuranceIncomeAccount,
                DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 2);
        assertJournalEntry(entries.get(0), loanPortfolioAccount, JournalEntryType.DEBIT, "400.00");
        assertJournalEntry(entries.get(1), insuranceIncomeAccount, JournalEntryType.CREDIT, "400.00");
        assertAmount("400.00", (BigDecimal) changes.get("insuranceCustomerBalanceIncrease"));
        assertEquals(101L, changes.get("insuranceLoanPortfolioGlAccountId"));
    }

    @Test
    void downwardInsuranceAdjustmentReducesInsuranceIncomeAndCustomerLoanPortfolioBalance() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class,
                CALLS_REAL_METHODS);
        final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
        final ProductToGLAccountMappingRepository mappingRepository = mock(ProductToGLAccountMappingRepository.class);
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount insuranceIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = insuranceAdjustmentTransaction(2L, new BigDecimal("400.00"), true);
        final Map<String, Object> changes = new HashMap<>();

        ReflectionTestUtils.setField(service, "journalEntryRepository", journalEntryRepository);
        ReflectionTestUtils.setField(service, "productToGLAccountMappingRepository", mappingRepository);
        when(mappingRepository.findCoreProductToFinAccountMapping(11L, PortfolioProductType.LOAN.getValue(),
                AccountingConstants.CashAccountsForLoan.LOAN_PORTFOLIO.getValue()))
                .thenReturn(ProductToGLAccountMapping.createNew(loanPortfolioAccount, 11L,
                        PortfolioProductType.LOAN.getValue(),
                        AccountingConstants.CashAccountsForLoan.LOAN_PORTFOLIO.getValue()));

        ReflectionTestUtils.invokeMethod(service, "postInsuranceCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, new BigDecimal("400.00"), BigDecimal.ZERO, insuranceIncomeAccount, insuranceIncomeAccount,
                DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 2);
        assertJournalEntry(entries.get(0), insuranceIncomeAccount, JournalEntryType.DEBIT, "400.00");
        assertJournalEntry(entries.get(1), loanPortfolioAccount, JournalEntryType.CREDIT, "400.00");
        assertAmount("400.00", (BigDecimal) changes.get("insuranceCustomerBalanceDecrease"));
        assertEquals(101L, changes.get("insuranceLoanPortfolioGlAccountId"));
    }

    private Loan singleDisbursementLoan(final BigDecimal approvedPrincipal, final LoanDisbursementDetails disbursementDetails,
            final LoanTransaction... transactions) {
        final Loan loan = new TestLoan();
        final LoanProduct loanProduct = mock(LoanProduct.class);
        final LoanProductRelatedDetail scheduleDetail = mock(LoanProductRelatedDetail.class);
        when(loanProduct.isMultiDisburseLoan()).thenReturn(false);
        when(scheduleDetail.getCurrency()).thenReturn(KES);

        ReflectionTestUtils.setField(loan, "loanProduct", loanProduct);
        ReflectionTestUtils.setField(loan, "loanRepaymentScheduleDetail", scheduleDetail);
        ReflectionTestUtils.setField(loan, "approvedPrincipal", approvedPrincipal);
        ReflectionTestUtils.setField(loan, "netDisbursalAmount", disbursementDetails.getNetDisbursalAmount());
        ReflectionTestUtils.setField(loan, "disbursementDetails", new ArrayList<>(List.of(disbursementDetails)));
        ReflectionTestUtils.setField(loan, "loanTransactions", new ArrayList<>(List.of(transactions)));

        disbursementDetails.updateLoan(loan);
        for (final LoanTransaction transaction : transactions) {
            transaction.updateLoan(loan);
        }
        return loan;
    }

    private LoanTransaction disbursement(final BigDecimal amount) {
        return LoanTransaction.disbursement(mock(Office.class), Money.of(KES, amount), null, DISBURSEMENT_DATE, null);
    }

    private LoanTransaction repaymentAtDisbursement(final BigDecimal amount, final boolean reversed) {
        final LoanTransaction repaymentAtDisbursement = LoanTransaction.repaymentAtDisbursement(mock(Office.class), Money.of(KES, amount),
                null, DISBURSEMENT_DATE, null);
        if (reversed) {
            repaymentAtDisbursement.reverse();
        }
        return repaymentAtDisbursement;
    }

    private Loan accountingLoan() {
        final Loan loan = mock(Loan.class);
        final Office office = mock(Office.class);
        when(loan.productId()).thenReturn(11L);
        when(loan.getOffice()).thenReturn(office);
        when(loan.getCurrency()).thenReturn(KES);
        when(loan.getId()).thenReturn(427367L);
        return loan;
    }

    private GLAccount glAccount(final Long id) {
        final GLAccount glAccount = mock(GLAccount.class);
        when(glAccount.getId()).thenReturn(id);
        return glAccount;
    }

    private LoanTransaction insuranceAdjustmentTransaction(final Long id, final BigDecimal amount, final boolean credit) {
        final LoanTransaction transaction = LoanTransaction.insuranceChargeAdjustment(mock(Loan.class), mock(Office.class),
                Money.of(KES, amount), DISBURSEMENT_DATE, credit);
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private List<JournalEntry> captureJournalEntries(final JournalEntryRepository journalEntryRepository, final int count) {
        final ArgumentCaptor<JournalEntry> journalEntryCaptor = ArgumentCaptor.forClass(JournalEntry.class);
        verify(journalEntryRepository, times(count)).save(journalEntryCaptor.capture());
        return journalEntryCaptor.getAllValues();
    }

    private void assertJournalEntry(final JournalEntry journalEntry, final GLAccount glAccount, final JournalEntryType type,
            final String amount) {
        assertSame(glAccount, journalEntry.getGlAccount());
        assertEquals(type.getValue(), journalEntry.getType());
        assertAmount(amount, journalEntry.getAmount());
    }

    private void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static final class TestLoan extends Loan {

        private TestLoan() {}
    }
}

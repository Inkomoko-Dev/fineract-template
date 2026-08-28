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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
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
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanCharge;
import org.apache.fineract.portfolio.loanaccount.domain.LoanChargePaidBy;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DisbursementChargeAdjustmentAccountingTest {

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
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, DISBURSEMENT_DATE)));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void upwardDisbursementChargeAdjustmentPostsCustomerBalanceToLoanPortfolioAndChargeIncome() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount chargeIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = chargeAdjustmentTransaction(1L, new BigDecimal("400.00"), false);
        applyProcessedCredit(adjustmentTransaction, new BigDecimal("400.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);

        ReflectionTestUtils.invokeMethod(service, "postChargeCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, allocation(new BigDecimal("800.00"), new BigDecimal("1200.00"), new BigDecimal("1200.00")),
                null, chargeIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 2);
        assertJournalEntry(entries.get(0), loanPortfolioAccount, JournalEntryType.DEBIT, "400.00");
        assertJournalEntry(entries.get(1), chargeIncomeAccount, JournalEntryType.CREDIT, "400.00");
        assertAmount("400.00", (BigDecimal) changes.get("chargeCustomerBalanceIncrease"));
        assertEquals(101L, changes.get("chargeLoanPortfolioGlAccountId"));
    }

    @Test
    void upwardDisbursementChargeAdjustmentAfterPriorDownwardCreditRestoresLoanBalanceBeforeFeeReceivable() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount chargeIncomeAccount = glAccount(202L);
        final GLAccount feeReceivableAccount = glAccount(404L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = chargeAdjustmentTransaction(8L, new BigDecimal("1000.00"), false);
        applyProcessedCredit(adjustmentTransaction, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);
        stubFeeReceivableAccount(mappingRepository, feeReceivableAccount);

        ReflectionTestUtils.invokeMethod(service, "postChargeCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, allocation(new BigDecimal("500.00"), new BigDecimal("1500.00"), new BigDecimal("1000.00")),
                null, chargeIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 3);
        assertJournalEntry(entries.get(0), loanPortfolioAccount, JournalEntryType.DEBIT, "500.00");
        assertJournalEntry(entries.get(1), feeReceivableAccount, JournalEntryType.DEBIT, "500.00");
        assertJournalEntry(entries.get(2), chargeIncomeAccount, JournalEntryType.CREDIT, "1000.00");
        assertAmount("500.00", (BigDecimal) changes.get("chargeCustomerBalanceIncrease"));
        assertAmount("500.00", (BigDecimal) changes.get("chargeFeeReceivableIncrease"));
        assertEquals(101L, changes.get("chargeLoanPortfolioGlAccountId"));
        assertEquals(404L, changes.get("chargeFeeReceivableGlAccountId"));
    }

    @Test
    void upwardDisbursementChargeAdjustmentDebitsOverpaymentWhenPriorCreditExceededLoanBalance() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount overpaymentAccount = glAccount(303L);
        final GLAccount chargeIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = chargeAdjustmentTransaction(9L, new BigDecimal("700.00"), false);
        applyProcessedCredit(adjustmentTransaction, new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("500.00"));
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);
        stubOverpaymentAccount(mappingRepository, overpaymentAccount);

        ReflectionTestUtils.invokeMethod(service, "postChargeCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, allocation(BigDecimal.ZERO, new BigDecimal("700.00"), new BigDecimal("700.00")), null,
                chargeIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 3);
        assertJournalEntry(entries.get(0), loanPortfolioAccount, JournalEntryType.DEBIT, "200.00");
        assertJournalEntry(entries.get(1), overpaymentAccount, JournalEntryType.DEBIT, "500.00");
        assertJournalEntry(entries.get(2), chargeIncomeAccount, JournalEntryType.CREDIT, "700.00");
        assertAmount("200.00", (BigDecimal) changes.get("chargeLoanPortfolioBalanceIncrease"));
        assertAmount("500.00", (BigDecimal) changes.get("chargeCustomerCreditDecrease"));
        assertEquals(303L, changes.get("chargeOverpaymentGlAccountId"));
    }

    @Test
    void downwardDisbursementChargeAdjustmentReducesChargeIncomeAndCustomerLoanPortfolioBalance() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount chargeIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = chargeAdjustmentTransaction(2L, new BigDecimal("400.00"), true);
        applyProcessedCredit(adjustmentTransaction, new BigDecimal("400.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);

        ReflectionTestUtils.invokeMethod(service, "postChargeCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, allocation(new BigDecimal("1200.00"), new BigDecimal("800.00"), new BigDecimal("1200.00")),
                chargeIncomeAccount, chargeIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 2);
        assertJournalEntry(entries.get(0), chargeIncomeAccount, JournalEntryType.DEBIT, "400.00");
        assertJournalEntry(entries.get(1), loanPortfolioAccount, JournalEntryType.CREDIT, "400.00");
        assertAmount("400.00", (BigDecimal) changes.get("chargeCustomerBalanceDecrease"));
        assertAmount("400.00", (BigDecimal) changes.get("chargeLoanPortfolioBalanceDecrease"));
        assertEquals(101L, changes.get("chargeLoanPortfolioGlAccountId"));
    }

    @Test
    void downwardDisbursementChargeAdjustmentSeparatesPaidCreditFromUnpaidFeeReduction() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount chargeIncomeAccount = glAccount(202L);
        final GLAccount feeReceivableAccount = glAccount(404L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = chargeAdjustmentTransaction(7L, new BigDecimal("400.00"), true);
        applyProcessedCredit(adjustmentTransaction, new BigDecimal("400.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);
        stubFeeReceivableAccount(mappingRepository, feeReceivableAccount);

        ReflectionTestUtils.invokeMethod(service, "postChargeCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, allocation(new BigDecimal("1000.00"), new BigDecimal("400.00"), new BigDecimal("800.00")),
                chargeIncomeAccount, chargeIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 3);
        assertJournalEntry(entries.get(0), chargeIncomeAccount, JournalEntryType.DEBIT, "600.00");
        assertJournalEntry(entries.get(1), loanPortfolioAccount, JournalEntryType.CREDIT, "400.00");
        assertJournalEntry(entries.get(2), feeReceivableAccount, JournalEntryType.CREDIT, "200.00");
        assertAmount("400.00", (BigDecimal) changes.get("chargeCustomerBalanceDecrease"));
        assertAmount("400.00", (BigDecimal) changes.get("chargeLoanPortfolioBalanceDecrease"));
        assertAmount("200.00", (BigDecimal) changes.get("chargeFeeReceivableDecrease"));
        assertEquals(404L, changes.get("chargeFeeReceivableGlAccountId"));
    }

    @Test
    void downwardDisbursementChargeAdjustmentUsesChargeAmountDeltaEvenWhenFeeOutstandingDoesNotChange() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount chargeIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = chargeAdjustmentTransaction(3L, new BigDecimal("1000.00"), true);
        applyProcessedCredit(adjustmentTransaction, new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);

        ReflectionTestUtils.invokeMethod(service, "postChargeCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, allocation(new BigDecimal("2000.00"), new BigDecimal("1000.00"), new BigDecimal("2000.00")),
                chargeIncomeAccount, chargeIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 2);
        assertJournalEntry(entries.get(0), chargeIncomeAccount, JournalEntryType.DEBIT, "1000.00");
        assertJournalEntry(entries.get(1), loanPortfolioAccount, JournalEntryType.CREDIT, "1000.00");
        assertAmount("1000.00", (BigDecimal) changes.get("chargeCustomerBalanceDecrease"));
        assertAmount("1000.00", (BigDecimal) changes.get("chargeLoanPortfolioBalanceDecrease"));
    }

    @Test
    void downwardDisbursementChargeAdjustmentCreditsCustomerOverpaymentWhenDecreaseExceedsOutstanding() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount overpaymentAccount = glAccount(303L);
        final GLAccount chargeIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = chargeAdjustmentTransaction(4L, new BigDecimal("1000.00"), true);
        applyProcessedCredit(adjustmentTransaction, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("500.00"));
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);
        stubOverpaymentAccount(mappingRepository, overpaymentAccount);

        ReflectionTestUtils.invokeMethod(service, "postChargeCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, allocation(new BigDecimal("2000.00"), new BigDecimal("1000.00"), new BigDecimal("2000.00")),
                chargeIncomeAccount, chargeIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 3);
        assertJournalEntry(entries.get(0), chargeIncomeAccount, JournalEntryType.DEBIT, "1000.00");
        assertJournalEntry(entries.get(1), loanPortfolioAccount, JournalEntryType.CREDIT, "500.00");
        assertJournalEntry(entries.get(2), overpaymentAccount, JournalEntryType.CREDIT, "500.00");
        assertAmount("1000.00", (BigDecimal) changes.get("chargeCustomerBalanceDecrease"));
        assertAmount("500.00", (BigDecimal) changes.get("chargeLoanPortfolioBalanceDecrease"));
        assertAmount("500.00", (BigDecimal) changes.get("chargeCustomerCredit"));
        assertEquals(303L, changes.get("chargeOverpaymentGlAccountId"));
    }

    @Test
    void replacementRepaymentAtDisbursementDoesNotCarryChargeReductionAsOverpayment() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final Loan loan = accountingLoan();
        final LoanCharge charge = mock(LoanCharge.class);
        when(charge.isPenaltyCharge()).thenReturn(false);
        final LoanTransaction replacementTransaction = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("2000.00")), null, DISBURSEMENT_DATE, null);
        replacementTransaction.getLoanChargesPaid().add(new LoanChargePaidBy(replacementTransaction, charge,
                new BigDecimal("1000.00"), null));

        ReflectionTestUtils.invokeMethod(service, "updateRepaymentAtDisbursementTransactionAmount", loan, replacementTransaction,
                Money.zero(KES));

        assertAmount("1000.00", replacementTransaction.getAmount(KES).getAmount());
        assertAmount("1000.00", replacementTransaction.getFeeChargesPortion(KES).getAmount());
        assertAmount("0.00", replacementTransaction.getOverPaymentPortion(KES).getAmount());
        assertAmount("0.00", replacementTransaction.getPrincipalPortion(KES).getAmount());
        assertAmount("0.00", replacementTransaction.getInterestPortion(KES).getAmount());
    }

    @Test
    void configuredChargeIncomeGlAccountUsesChargeSpecificFeeIncomeMapping() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final Loan loan = accountingLoan();
        final GLAccount chargeIncomeAccount = glAccount(202L);
        final Charge charge = mock(Charge.class);
        final LoanCharge loanCharge = mock(LoanCharge.class);
        when(charge.getId()).thenReturn(4L);
        when(loanCharge.getCharge()).thenReturn(charge);
        when(mappingRepository.findProductIdAndProductTypeAndFinancialAccountTypeAndChargeId(11L,
                PortfolioProductType.LOAN.getValue(), AccountingConstants.CashAccountsForLoan.INCOME_FROM_FEES.getValue(), 4L))
                .thenReturn(new ProductToGLAccountMapping(chargeIncomeAccount, 11L, PortfolioProductType.LOAN.getValue(),
                        AccountingConstants.CashAccountsForLoan.INCOME_FROM_FEES.getValue(), charge));

        final GLAccount resolvedAccount = ReflectionTestUtils.invokeMethod(service, "findConfiguredChargeIncomeGlAccount", loan,
                loanCharge);

        assertSame(chargeIncomeAccount, resolvedAccount);
    }

    private LoanWritePlatformServiceJpaRepositoryImpl serviceWithAccountingRepositories() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = mock(LoanWritePlatformServiceJpaRepositoryImpl.class,
                CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "journalEntryRepository", mock(JournalEntryRepository.class));
        ReflectionTestUtils.setField(service, "productToGLAccountMappingRepository", mock(ProductToGLAccountMappingRepository.class));
        return service;
    }

    private void stubLoanPortfolioAccount(final ProductToGLAccountMappingRepository mappingRepository, final GLAccount account) {
        when(mappingRepository.findCoreProductToFinAccountMapping(11L, PortfolioProductType.LOAN.getValue(),
                AccountingConstants.CashAccountsForLoan.LOAN_PORTFOLIO.getValue()))
                .thenReturn(ProductToGLAccountMapping.createNew(account, 11L, PortfolioProductType.LOAN.getValue(),
                        AccountingConstants.CashAccountsForLoan.LOAN_PORTFOLIO.getValue()));
    }

    private void stubOverpaymentAccount(final ProductToGLAccountMappingRepository mappingRepository, final GLAccount account) {
        when(mappingRepository.findCoreProductToFinAccountMapping(11L, PortfolioProductType.LOAN.getValue(),
                AccountingConstants.CashAccountsForLoan.OVERPAYMENT.getValue()))
                .thenReturn(ProductToGLAccountMapping.createNew(account, 11L, PortfolioProductType.LOAN.getValue(),
                        AccountingConstants.CashAccountsForLoan.OVERPAYMENT.getValue()));
    }

    private void stubFeeReceivableAccount(final ProductToGLAccountMappingRepository mappingRepository, final GLAccount account) {
        when(mappingRepository.findCoreProductToFinAccountMapping(11L, PortfolioProductType.LOAN.getValue(),
                AccountingConstants.AccrualAccountsForLoan.FEES_RECEIVABLE.getValue()))
                .thenReturn(ProductToGLAccountMapping.createNew(account, 11L, PortfolioProductType.LOAN.getValue(),
                        AccountingConstants.AccrualAccountsForLoan.FEES_RECEIVABLE.getValue()));
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

    private LoanTransaction chargeAdjustmentTransaction(final Long id, final BigDecimal amount, final boolean credit) {
        final LoanTransaction transaction = LoanTransaction.disbursementChargeAdjustment(mock(Loan.class), mock(Office.class),
                Money.of(KES, amount), DISBURSEMENT_DATE, credit);
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private void applyProcessedCredit(final LoanTransaction transaction, final BigDecimal principal, final BigDecimal interest,
            final BigDecimal penalty, final BigDecimal overpayment) {
        transaction.updateComponents(Money.of(KES, principal), Money.of(KES, interest), Money.zero(KES), Money.of(KES, penalty));
        transaction.replaceOverPaymentPortion(Money.of(KES, overpayment));
    }

    private DisbursementChargeAdjustmentAllocation allocation(final BigDecimal previousAmount, final BigDecimal newAmount,
            final BigDecimal paidAtDisbursementAmount) {
        return DisbursementChargeAdjustmentAllocation.from(previousAmount, newAmount, paidAtDisbursementAmount);
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
}

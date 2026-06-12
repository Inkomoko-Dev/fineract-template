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

class InsuranceAdjustmentAccountingTest {

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
    void upwardInsuranceAdjustmentPostsCustomerBalanceToLoanPortfolioAndInsuranceIncome() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount insuranceIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = insuranceAdjustmentTransaction(1L, new BigDecimal("400.00"), false);
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);

        ReflectionTestUtils.invokeMethod(service, "postInsuranceCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, new BigDecimal("800.00"), new BigDecimal("1200.00"), new BigDecimal("5250.00"), null,
                insuranceIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 2);
        assertJournalEntry(entries.get(0), loanPortfolioAccount, JournalEntryType.DEBIT, "400.00");
        assertJournalEntry(entries.get(1), insuranceIncomeAccount, JournalEntryType.CREDIT, "400.00");
        assertAmount("400.00", (BigDecimal) changes.get("insuranceCustomerBalanceIncrease"));
        assertEquals(101L, changes.get("insuranceLoanPortfolioGlAccountId"));
    }

    @Test
    void downwardInsuranceAdjustmentReducesInsuranceIncomeAndCustomerLoanPortfolioBalance() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount insuranceIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = insuranceAdjustmentTransaction(2L, new BigDecimal("400.00"), true);
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);

        ReflectionTestUtils.invokeMethod(service, "postInsuranceCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, new BigDecimal("1200.00"), new BigDecimal("800.00"), new BigDecimal("5250.00"),
                insuranceIncomeAccount, insuranceIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 2);
        assertJournalEntry(entries.get(0), insuranceIncomeAccount, JournalEntryType.DEBIT, "400.00");
        assertJournalEntry(entries.get(1), loanPortfolioAccount, JournalEntryType.CREDIT, "400.00");
        assertAmount("400.00", (BigDecimal) changes.get("insuranceCustomerBalanceDecrease"));
        assertAmount("400.00", (BigDecimal) changes.get("insuranceLoanPortfolioBalanceDecrease"));
        assertEquals(101L, changes.get("insuranceLoanPortfolioGlAccountId"));
    }

    @Test
    void downwardInsuranceAdjustmentUsesInsuranceAmountDeltaEvenWhenFeeOutstandingDoesNotChange() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount insuranceIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = insuranceAdjustmentTransaction(3L, new BigDecimal("1000.00"), true);
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);

        ReflectionTestUtils.invokeMethod(service, "postInsuranceCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, new BigDecimal("2000.00"), new BigDecimal("1000.00"), new BigDecimal("5250.00"),
                insuranceIncomeAccount, insuranceIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 2);
        assertJournalEntry(entries.get(0), insuranceIncomeAccount, JournalEntryType.DEBIT, "1000.00");
        assertJournalEntry(entries.get(1), loanPortfolioAccount, JournalEntryType.CREDIT, "1000.00");
        assertAmount("1000.00", (BigDecimal) changes.get("insuranceCustomerBalanceDecrease"));
        assertAmount("1000.00", (BigDecimal) changes.get("insuranceLoanPortfolioBalanceDecrease"));
    }

    @Test
    void downwardInsuranceAdjustmentCreditsCustomerOverpaymentWhenDecreaseExceedsOutstanding() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final JournalEntryRepository journalEntryRepository = (JournalEntryRepository) ReflectionTestUtils.getField(service,
                "journalEntryRepository");
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final GLAccount loanPortfolioAccount = glAccount(101L);
        final GLAccount overpaymentAccount = glAccount(303L);
        final GLAccount insuranceIncomeAccount = glAccount(202L);
        final Loan loan = accountingLoan();
        final LoanTransaction adjustmentTransaction = insuranceAdjustmentTransaction(4L, new BigDecimal("1000.00"), true);
        final Map<String, Object> changes = new HashMap<>();
        stubLoanPortfolioAccount(mappingRepository, loanPortfolioAccount);
        stubOverpaymentAccount(mappingRepository, overpaymentAccount);

        ReflectionTestUtils.invokeMethod(service, "postInsuranceCustomerBalanceAdjustmentJournalEntries", loan,
                adjustmentTransaction, new BigDecimal("2000.00"), new BigDecimal("1000.00"), new BigDecimal("500.00"),
                insuranceIncomeAccount, insuranceIncomeAccount, DISBURSEMENT_DATE, changes);

        final List<JournalEntry> entries = captureJournalEntries(journalEntryRepository, 3);
        assertJournalEntry(entries.get(0), insuranceIncomeAccount, JournalEntryType.DEBIT, "1000.00");
        assertJournalEntry(entries.get(1), loanPortfolioAccount, JournalEntryType.CREDIT, "500.00");
        assertJournalEntry(entries.get(2), overpaymentAccount, JournalEntryType.CREDIT, "500.00");
        assertAmount("1000.00", (BigDecimal) changes.get("insuranceCustomerBalanceDecrease"));
        assertAmount("500.00", (BigDecimal) changes.get("insuranceLoanPortfolioBalanceDecrease"));
        assertAmount("500.00", (BigDecimal) changes.get("insuranceCustomerCredit"));
        assertEquals(303L, changes.get("insuranceOverpaymentGlAccountId"));
    }

    @Test
    void replacementRepaymentAtDisbursementDoesNotCarryInsuranceReductionAsOverpayment() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final Loan loan = accountingLoan();
        final LoanCharge insuranceCharge = mock(LoanCharge.class);
        when(insuranceCharge.isPenaltyCharge()).thenReturn(false);
        final LoanTransaction replacementTransaction = LoanTransaction.repaymentAtDisbursement(mock(Office.class),
                Money.of(KES, new BigDecimal("2000.00")), null, DISBURSEMENT_DATE, null);
        replacementTransaction.getLoanChargesPaid().add(new LoanChargePaidBy(replacementTransaction, insuranceCharge,
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
    void configuredInsuranceIncomeGlAccountUsesChargeSpecificFeeIncomeMapping() {
        final LoanWritePlatformServiceJpaRepositoryImpl service = serviceWithAccountingRepositories();
        final ProductToGLAccountMappingRepository mappingRepository = (ProductToGLAccountMappingRepository) ReflectionTestUtils
                .getField(service, "productToGLAccountMappingRepository");
        final Loan loan = accountingLoan();
        final GLAccount insuranceIncomeAccount = glAccount(202L);
        final Charge charge = mock(Charge.class);
        final LoanCharge insuranceCharge = mock(LoanCharge.class);
        when(charge.getId()).thenReturn(4L);
        when(insuranceCharge.getCharge()).thenReturn(charge);
        when(mappingRepository.findProductIdAndProductTypeAndFinancialAccountTypeAndChargeId(11L,
                PortfolioProductType.LOAN.getValue(), AccountingConstants.CashAccountsForLoan.INCOME_FROM_FEES.getValue(), 4L))
                .thenReturn(new ProductToGLAccountMapping(insuranceIncomeAccount, 11L, PortfolioProductType.LOAN.getValue(),
                        AccountingConstants.CashAccountsForLoan.INCOME_FROM_FEES.getValue(), charge));

        final GLAccount resolvedAccount = ReflectionTestUtils.invokeMethod(service, "findConfiguredInsuranceIncomeGlAccount", loan,
                insuranceCharge);

        assertSame(insuranceIncomeAccount, resolvedAccount);
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
}

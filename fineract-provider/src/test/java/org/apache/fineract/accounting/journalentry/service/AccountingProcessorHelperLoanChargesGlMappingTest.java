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
package org.apache.fineract.accounting.journalentry.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import org.apache.fineract.accounting.common.AccountingConstants.CashAccountsForLoan;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.producttoaccountmapping.domain.PortfolioProductType;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMapping;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountingProcessorHelperLoanChargesGlMappingTest {

    private static final Long LOAN_PRODUCT_ID = 10L;
    private static final Long CHARGE_ID = 99L;

    @Mock
    private ProductToGLAccountMappingRepository accountMappingRepository;

    @Mock
    private ChargeRepositoryWrapper chargeRepositoryWrapper;

    // other dependencies are not needed by this test, but are required by constructor
    @Mock
    private org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository glJournalEntryRepository;
    @Mock
    private org.apache.fineract.accounting.financialactivityaccount.domain.FinancialActivityAccountRepositoryWrapper financialActivityAccountRepository;
    @Mock
    private org.apache.fineract.accounting.closure.domain.GLClosureRepository closureRepository;
    @Mock
    private org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper accountRepositoryWrapper;
    @Mock
    private org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper officeRepositoryWrapper;
    @Mock
    private org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionRepository loanTransactionRepository;
    @Mock
    private org.apache.fineract.portfolio.client.domain.ClientTransactionRepositoryWrapper clientTransactionRepository;
    @Mock
    private org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository savingsAccountTransactionRepository;
    @Mock
    private org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService accountTransfersReadPlatformService;
    @Mock
    private org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService configurationDomainService;
    @Mock
    private org.apache.fineract.infrastructure.Odoo.OdooService oddoService;

    @InjectMocks
    private AccountingProcessorHelper helper;

    @Test
    void getLinkedGLAccountForLoanCharges_prefersChargeSpecificProductMappingOverChargeAccount() {
        final GLAccount coreIncome = mockGlAccount();
        final GLAccount chargeSpecificIncome = mockGlAccount();
        final GLAccount chargeAccount = mockGlAccount();

        givenCoreIncomeMapping(coreIncome);
        givenChargeSpecificIncomeMapping(chargeSpecificIncome);
        givenChargeAccount(chargeAccount);

        final GLAccount result = invokeGetLinkedGlAccountForLoanCharges(CashAccountsForLoan.INCOME_FROM_FEES.getValue(), CHARGE_ID);
        assertSame(chargeSpecificIncome, result);
    }

    @Test
    void getLinkedGLAccountForLoanCharges_fallsBackToChargeAccountWhenNoChargeSpecificMapping() {
        final GLAccount coreIncome = mockGlAccount();
        final GLAccount chargeAccount = mockGlAccount();

        givenCoreIncomeMapping(coreIncome);
        givenNoChargeSpecificIncomeMapping();
        givenChargeAccount(chargeAccount);

        final GLAccount result = invokeGetLinkedGlAccountForLoanCharges(CashAccountsForLoan.INCOME_FROM_FEES.getValue(), CHARGE_ID);
        assertSame(chargeAccount, result);
    }

    @Test
    void getLinkedGLAccountForLoanCharges_fallsBackToCoreProductMappingWhenChargeAccountIsNull() {
        final GLAccount coreIncome = mockGlAccount();

        givenCoreIncomeMapping(coreIncome);
        givenNoChargeSpecificIncomeMapping();
        givenChargeAccount(null);

        final GLAccount result = invokeGetLinkedGlAccountForLoanCharges(CashAccountsForLoan.INCOME_FROM_FEES.getValue(), CHARGE_ID);
        assertSame(coreIncome, result);
    }

    private GLAccount invokeGetLinkedGlAccountForLoanCharges(final int accountMappingTypeId, final Long chargeId) {
        return ReflectionTestUtils.invokeMethod(helper, "getLinkedGLAccountForLoanCharges", LOAN_PRODUCT_ID, accountMappingTypeId, chargeId);
    }

    private void givenCoreIncomeMapping(final GLAccount glAccount) {
        final ProductToGLAccountMapping core = org.mockito.Mockito.mock(ProductToGLAccountMapping.class);
        given(core.getGlAccount()).willReturn(glAccount);
        given(accountMappingRepository.findCoreProductToFinAccountMapping(LOAN_PRODUCT_ID, PortfolioProductType.LOAN.getValue(),
                CashAccountsForLoan.INCOME_FROM_FEES.getValue())).willReturn(core);
    }

    private void givenChargeSpecificIncomeMapping(final GLAccount glAccount) {
        final ProductToGLAccountMapping specific = org.mockito.Mockito.mock(ProductToGLAccountMapping.class);
        given(specific.getGlAccount()).willReturn(glAccount);
        given(accountMappingRepository.findProductIdAndProductTypeAndFinancialAccountTypeAndChargeId(LOAN_PRODUCT_ID,
                PortfolioProductType.LOAN.getValue(), CashAccountsForLoan.INCOME_FROM_FEES.getValue(), CHARGE_ID)).willReturn(specific);
    }

    private void givenNoChargeSpecificIncomeMapping() {
        given(accountMappingRepository.findProductIdAndProductTypeAndFinancialAccountTypeAndChargeId(LOAN_PRODUCT_ID,
                PortfolioProductType.LOAN.getValue(), CashAccountsForLoan.INCOME_FROM_FEES.getValue(), CHARGE_ID)).willReturn(null);
    }

    private void givenChargeAccount(final GLAccount glAccount) {
        final Charge charge = org.mockito.Mockito.mock(Charge.class);
        given(charge.getAccount()).willReturn(glAccount);
        given(chargeRepositoryWrapper.findOneWithNotFoundDetection(CHARGE_ID)).willReturn(charge);
    }

    private GLAccount mockGlAccount() {
        return org.mockito.Mockito.mock(GLAccount.class);
    }
}


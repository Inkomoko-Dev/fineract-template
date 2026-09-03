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
package org.apache.fineract.portfolio.loanproduct.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.fineract.portfolio.businessevent.domain.loan.product.LoanProductCreateBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.product.LoanProductUpdateBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductData;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.event.DisbursementPartnerWebhookPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThirdPartyDisbursementLoanProductSyncServiceTest {

    @Mock
    private BusinessEventNotifierService businessEventNotifierService;

    @Mock
    private ThirdPartyDisbursementProductReadPlatformService productReadPlatformService;

    @Mock
    private DisbursementPartnerWebhookPublisher eventPublisher;

    @Mock
    private LoanProduct loanProduct;

    private ThirdPartyDisbursementLoanProductSyncService underTest;

    @BeforeEach
    void setUp() {
        this.underTest = new ThirdPartyDisbursementLoanProductSyncService(this.businessEventNotifierService,
                this.productReadPlatformService, this.eventPublisher);
        given(this.loanProduct.getId()).willReturn(5L);
    }

    @Test
    void publishesCreateWhenProductFlagEnabled() {
        given(this.loanProduct.isEnableThirdPartyDisbursement()).willReturn(true);
        final ThirdPartyDisbursementProductData productData = org.mockito.Mockito.mock(ThirdPartyDisbursementProductData.class);
        given(this.productReadPlatformService.retrieveOne(5L)).willReturn(productData);

        this.underTest.notifyProductChanged(this.loanProduct, "CREATE");

        verify(this.eventPublisher).publish(eq("KIFIYA"), eq("CREATE"), eq(productData));
    }

    @Test
    void publishesDeactivateWhenProductFlagDisabled() {
        given(this.loanProduct.isEnableThirdPartyDisbursement()).willReturn(false);
        given(this.loanProduct.productName()).willReturn("BNPL Product");

        this.underTest.notifyProductChanged(this.loanProduct, "DEACTIVATE");

        verify(this.productReadPlatformService, never()).retrieveOne(any());
        verify(this.eventPublisher).publish(eq("KIFIYA"), eq("DEACTIVATE"), any(ThirdPartyDisbursementProductData.class));
    }

    @Test
    void registersCreateAndUpdateListeners() {
        this.underTest.addListeners();

        verify(this.businessEventNotifierService).addPostBusinessEventListener(eq(LoanProductCreateBusinessEvent.class), any());
        verify(this.businessEventNotifierService).addPostBusinessEventListener(eq(LoanProductUpdateBusinessEvent.class), any());
    }
}

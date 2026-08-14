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

import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.businessevent.BusinessEventListener;
import org.apache.fineract.portfolio.businessevent.domain.loan.product.LoanProductCreateBusinessEvent;
import org.apache.fineract.portfolio.businessevent.domain.loan.product.LoanProductUpdateBusinessEvent;
import org.apache.fineract.portfolio.businessevent.service.BusinessEventNotifierService;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductData;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.loanproduct.event.DisbursementPartnerWebhookPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThirdPartyDisbursementLoanProductSyncService {

    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_DEACTIVATE = "DEACTIVATE";
    private static final String DEFAULT_PARTNER = "KIFIYA";

    private final BusinessEventNotifierService businessEventNotifierService;
    private final ThirdPartyDisbursementProductReadPlatformService productReadPlatformService;
    private final DisbursementPartnerWebhookPublisher eventPublisher;

    @PostConstruct
    public void addListeners() {
        this.businessEventNotifierService.addPostBusinessEventListener(LoanProductCreateBusinessEvent.class,
                new LoanProductCreatedSyncListener());
        this.businessEventNotifierService.addPostBusinessEventListener(LoanProductUpdateBusinessEvent.class,
                new LoanProductUpdatedSyncListener());
    }

    void notifyProductChanged(final LoanProduct loanProduct, final String action) {
        try {
            // Use default partner since provider is selected at loan level, not product level
            final String partnerCode = DEFAULT_PARTNER;
            
            if (ACTION_DEACTIVATE.equals(action)) {
                final ThirdPartyDisbursementProductData payload = ThirdPartyDisbursementProductData.deactivated(loanProduct);
                this.eventPublisher.publish(partnerCode, action, payload);
                return;
            }

            final ThirdPartyDisbursementProductData product = this.productReadPlatformService.retrieveOne(loanProduct.getId());
            this.eventPublisher.publish(partnerCode, action, product);
        } catch (Exception ex) {
            log.error("Failed to sync third-party disbursement loan product {} on {}: {}", loanProduct.getId(), action, ex.getMessage(),
                    ex);
        }
    }

    private class LoanProductCreatedSyncListener implements BusinessEventListener<LoanProductCreateBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanProductCreateBusinessEvent event) {
            final LoanProduct loanProduct = event.get();
            if (loanProduct.isEnableThirdPartyDisbursement()) {
                notifyProductChanged(loanProduct, ACTION_CREATE);
            }
        }
    }

    private class LoanProductUpdatedSyncListener implements BusinessEventListener<LoanProductUpdateBusinessEvent> {

        @Override
        public void onBusinessEvent(final LoanProductUpdateBusinessEvent event) {
            final LoanProduct loanProduct = event.get();
            if (event.isThirdPartyDisbursementDeactivated()) {
                notifyProductChanged(loanProduct, ACTION_DEACTIVATE);
            } else if (loanProduct.isEnableThirdPartyDisbursement()) {
                notifyProductChanged(loanProduct, ACTION_UPDATE);
            }
        }
    }
}

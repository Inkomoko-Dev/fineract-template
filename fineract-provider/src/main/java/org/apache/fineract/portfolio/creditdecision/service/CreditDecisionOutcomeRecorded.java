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
package org.apache.fineract.portfolio.creditdecision.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Published when the decision engine's outcome for a loan application has been recorded. The loan account module listens
 * to it to carry out an INELIGIBLE directive, which keeps the credit decision code from depending on loan workflows.
 */
@Getter
@RequiredArgsConstructor
public final class CreditDecisionOutcomeRecorded {

    private final Long loanId;
    /** AUTO_APPROVE, IC_REVIEW_REQUIRED or INELIGIBLE. */
    private final String decision;
    private final String reasonCodes;
    /** The product's profile is Live, so the directive is carried out. In Shadow it is only recorded. */
    private final boolean live;
}

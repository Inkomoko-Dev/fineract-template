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
 * Published when scoring an application has failed and nothing more will happen on its own: the automatic retries ran
 * out, or the connection is set to alert the credit team instead of retrying. The alert listener tells the team.
 */
@Getter
@RequiredArgsConstructor
public final class CreditDecisionScoringFailed {

    private final Long loanId;
    private final String applicationRef;
    private final String message;
    /** How many sends have failed in a row. */
    private final int failures;
    /** The automatic retries ran out, as opposed to the connection being set to alert straight away. */
    private final boolean retriesExhausted;
}

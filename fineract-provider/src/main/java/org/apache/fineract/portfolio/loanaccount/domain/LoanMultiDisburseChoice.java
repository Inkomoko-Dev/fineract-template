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
package org.apache.fineract.portfolio.loanaccount.domain;

import com.google.gson.JsonElement;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;

/**
 * A product that allows multiple tranches does not make a loan multi-tranche. The loan is multi-tranche only when the
 * request explicitly asks for it. A missing choice, and an explicit false, stay a single disbursement. This applies to
 * loans already active and to loans still before approval.
 */
public final class LoanMultiDisburseChoice {

    private LoanMultiDisburseChoice() {}

    public static boolean selected(final boolean productAllowsMultipleDisbursals, final Boolean requested) {
        return productAllowsMultipleDisbursals && Boolean.TRUE.equals(requested);
    }

    public static Boolean requested(final FromJsonHelper helper, final JsonElement element) {
        if (helper == null || element == null || !helper.parameterExists(LoanApiConstants.multiDisburseLoanParameterName, element)) {
            return null;
        }
        return helper.extractBooleanNamed(LoanApiConstants.multiDisburseLoanParameterName, element);
    }
}

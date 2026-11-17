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
 * A product that allows multiple tranches does not force every loan to use them. A missing choice keeps the product
 * setting so existing loans stay multi-tranche. An explicit false makes that loan a single disbursement.
 */
public final class LoanMultiDisburseChoice {

    private LoanMultiDisburseChoice() {}

    public static boolean selected(final boolean productAllowsMultipleDisbursals, final Boolean requested) {
        if (!productAllowsMultipleDisbursals) {
            return false;
        }
        return requested == null || requested;
    }

    public static Boolean requested(final FromJsonHelper helper, final JsonElement element) {
        if (helper == null || element == null || !helper.parameterExists(LoanApiConstants.multiDisburseLoanParameterName, element)) {
            return null;
        }
        return helper.extractBooleanNamed(LoanApiConstants.multiDisburseLoanParameterName, element);
    }
}

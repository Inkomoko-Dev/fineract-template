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
package org.apache.fineract.portfolio.loanaccount.data;

import lombok.Getter;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;

@Getter
public class KenyaCapitalDisbursementDefaultsResult {

    private final boolean kenyaCapital;
    private final CodeValue department;
    private final String budgetLocation;
    private final boolean budgetReviewRequired;

    private KenyaCapitalDisbursementDefaultsResult(final boolean kenyaCapital, final CodeValue department, final String budgetLocation,
            final boolean budgetReviewRequired) {
        this.kenyaCapital = kenyaCapital;
        this.department = department;
        this.budgetLocation = budgetLocation;
        this.budgetReviewRequired = budgetReviewRequired;
    }

    public static KenyaCapitalDisbursementDefaultsResult notApplicable() {
        return new KenyaCapitalDisbursementDefaultsResult(false, null, null, false);
    }

    public static KenyaCapitalDisbursementDefaultsResult applicable(final CodeValue department, final String budgetLocation,
            final boolean budgetReviewRequired) {
        return new KenyaCapitalDisbursementDefaultsResult(true, department, budgetLocation, budgetReviewRequired);
    }

    public Long getDepartmentId() {
        return this.department != null ? this.department.getId() : null;
    }

    public String getDepartmentName() {
        return this.department != null ? this.department.label() : null;
    }
}

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
package org.apache.fineract.portfolio.loanclassification.data;

import java.util.List;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;

public final class LoanClassificationCountryConfigData {

    private final Long id;
    private final Long countryId;
    private final String countryName;
    private final List<LoanClassificationThresholdData> thresholds;
    private final List<CodeValueData> countryOptions;
    private final List<LoanClassificationThresholdData> defaultThresholds;

    private LoanClassificationCountryConfigData(final Long id, final Long countryId, final String countryName,
            final List<LoanClassificationThresholdData> thresholds, final List<CodeValueData> countryOptions,
            final List<LoanClassificationThresholdData> defaultThresholds) {
        this.id = id;
        this.countryId = countryId;
        this.countryName = countryName;
        this.thresholds = thresholds;
        this.countryOptions = countryOptions;
        this.defaultThresholds = defaultThresholds;
    }

    public static LoanClassificationCountryConfigData instance(final Long id, final Long countryId, final String countryName,
            final List<LoanClassificationThresholdData> thresholds) {
        return new LoanClassificationCountryConfigData(id, countryId, countryName, thresholds, null, null);
    }

    public static LoanClassificationCountryConfigData template(final List<CodeValueData> countryOptions,
            final List<LoanClassificationThresholdData> defaultThresholds) {
        return new LoanClassificationCountryConfigData(null, null, null, null, countryOptions, defaultThresholds);
    }

    public Long getId() {
        return this.id;
    }

    public Long getCountryId() {
        return this.countryId;
    }

    public String getCountryName() {
        return this.countryName;
    }

    public List<LoanClassificationThresholdData> getThresholds() {
        return this.thresholds;
    }

    public List<CodeValueData> getCountryOptions() {
        return this.countryOptions;
    }

    public List<LoanClassificationThresholdData> getDefaultThresholds() {
        return this.defaultThresholds;
    }
}

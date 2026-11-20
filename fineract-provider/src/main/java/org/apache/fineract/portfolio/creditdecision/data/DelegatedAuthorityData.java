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
package org.apache.fineract.portfolio.creditdecision.data;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Where an application the decision engine marked IC_REVIEW_REQUIRED enters the IC levels. */
public final class DelegatedAuthorityData {

    private DelegatedAuthorityData() {}

    /** One rule. An empty currency or risk tier means any, an empty maximum means no upper limit. */
    @Getter
    @Builder
    public static class RuleData {

        private final Long id;
        private final String currency;
        private final String riskTier;
        private final BigDecimal minAmount;
        private final BigDecimal maxAmount;
        private final Integer entryLevelNumber;
        private final String entryLevelName;
        /** The person whose queue the application lands in, when the officer does not name one. */
        private final Long approverUserId;
        private final String approverUsername;
        private final boolean active;
        private final String description;
        private final String updatedBy;
        private final String updatedOn;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class RuleRequest {

        private String currency;
        private String riskTier;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private Integer entryLevelNumber;
        private Long approverUserId;
        private Boolean active;
        private String description;
    }

    /** The level a rule sends an application to. */
    @Getter
    @Builder
    public static class EntryData {

        private final Long ruleId;
        private final Integer entryLevelNumber;
        private final Long approverUserId;
        private final String description;
    }
}

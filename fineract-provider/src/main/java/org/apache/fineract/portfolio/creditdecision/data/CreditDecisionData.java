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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request and response shapes of the credit decision API. Dates are ISO strings so the UI can show them as received.
 */
public final class CreditDecisionData {

    private CreditDecisionData() {}

    @Getter
    @AllArgsConstructor
    public static class CatalogueItem {

        private final String key;
        private final String defaultField;
        private final String label;
        private final String description;
        private final String defaultMissing;
    }

    @Getter
    @AllArgsConstructor
    public static class CatalogueGroup {

        private final String name;
        private final String source;
        private final List<CatalogueItem> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParamData {

        private String key;
        private boolean enabled;
        private String fieldName;
        private String missingPolicy;
    }

    @Getter
    @AllArgsConstructor
    public static class VersionData {

        private final Long id;
        private final int versionNo;
        /** DRAFT, PENDING, ACTIVE or RETIRED */
        private final String status;
        private final String reason;
        private final String preparedBy;
        private final String preparedOn;
        private final String decidedBy;
        private final String decidedOn;
        private final List<Long> productIds;
        private final List<ParamData> params;
    }

    @Getter
    @AllArgsConstructor
    public static class ProfileData {

        private final Long id;
        private final String name;
        /** OFF, SHADOW or LIVE */
        private final String mode;
        private final String createdBy;
        private final String createdOn;
        private final List<VersionData> versions;
    }

    @Getter
    @AllArgsConstructor
    public static class ProductData {

        private final Long id;
        private final String name;
        private final String currencyCode;
        private final Long claimedByProfileId;
        private final String claimedByProfileName;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionData {

        private String provider;
        private boolean enabled;
        private String endpointUrl;
        private int timeoutSeconds;
        private int retries;
        /** RETRY or ALERT */
        private String onNoReply;
        private String updatedBy;
        private String updatedOn;
    }

    /**
     * What the integration service needs to build one request: the live version of the profile that covers a product.
     */
    @Getter
    @AllArgsConstructor
    public static class ActiveProfileData {

        private final Long profileId;
        private final String profileName;
        private final String mode;
        private final int versionNo;
        private final boolean sendingEnabled;
        private final String provider;
        private final List<ParamData> params;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogEntryData {

        private Long id;
        private String applicationRef;
        private Long loanId;
        private String customerRef;
        private Long profileId;
        private String profileName;
        private Integer versionNo;
        private String mode;
        private String provider;
        /** DECIDED, HELD or AWAITING */
        private String status;
        private String decision;
        private BigDecimal score;
        private String reasonCodes;
        private Integer replyMs;
        private String nextStep;
        private String requestJson;
        private String replyJson;
        private String createdOn;
        private String riskTier;
        private BigDecimal maxExposure;
        private String modelVersion;
        /** The identifier the request carried, shared by the sends of one request that was retried. */
        private String requestId;
    }

    /** How many log rows each outcome has, under every filter except the outcome itself, so the filter tabs can show counts. */
    @Getter
    @AllArgsConstructor
    public static class LogCounts {

        private final long total;
        private final long autoApprove;
        private final long icReview;
        private final long ineligible;
        private final long held;
        private final long awaiting;
    }

    /** One page of the log. {@code total} counts every row that matches the filters, not just this page. */
    @Getter
    @AllArgsConstructor
    public static class LogPageData {

        private final List<LogEntryData> content;
        private final long total;
        private final int page;
        private final int pageSize;
        private final int totalPages;
        private final LogCounts counts;
        /** The risk tiers and model versions the log holds, to offer as filters. */
        private final List<String> riskTiers;
        private final List<String> modelVersions;
    }

    /** What the log can be narrowed by. Everything is optional; a blank value means no narrowing. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class LogFilter {

        /** AUTO_APPROVE, IC_REVIEW_REQUIRED, INELIGIBLE, or NONE for requests the engine has not answered. */
        private String decision;
        /** DECIDED, HELD or AWAITING. */
        private String status;
        private Long profileId;
        /** OFF, SHADOW or LIVE. */
        private String mode;
        /** Part of the application or customer reference, or the request identifier. */
        private String search;
        /** The risk tier the engine reported, exactly. */
        private String riskTier;
        /** The model version the engine reported, exactly. */
        private String modelVersion;
        /** Part of the reason codes. */
        private String reason;
        /** First day, as yyyy-MM-dd. */
        private String from;
        /** Last day, as yyyy-MM-dd. */
        private String to;
        /** date_desc (default), date_asc, score_desc or score_asc. */
        private String sort;
        /** First page is 1. */
        private int page = 1;
        private int pageSize = 25;
    }

    // ---- request bodies ----

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CreateProfileRequest {

        private String name;
        private List<Long> productIds;
        private Long copyFromProfileId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SaveDraftRequest {

        private List<ParamData> params;
        private List<Long> productIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class NewDraftRequest {

        /** Version to copy from. When empty the live version is used. */
        private Integer fromVersion;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class CommandRequest {

        private String reason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ModeRequest {

        private String mode;
    }
}

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shapes of the per-loan credit decision API: whether an application needs an automated credit decision, whether its
 * details were checked, and what the decision engine replied.
 */
public final class CreditDecisionLoanData {

    private CreditDecisionLoanData() {}

    public static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";
    public static final String VERIFIED = "VERIFIED";

    public static final String NOT_SENT = "NOT_SENT";
    public static final String DECIDED = "DECIDED";
    public static final String FAILED = "FAILED";

    /** Why the request cannot be sent right now. The screen words each one in the user's language. */
    public static final String BLOCKER_NOT_REQUIRED = "NOT_REQUIRED";
    public static final String BLOCKER_LOAN_NOT_PENDING = "LOAN_NOT_PENDING";
    public static final String BLOCKER_NOT_IN_REVIEW = "NOT_IN_REVIEW";
    public static final String BLOCKER_OUTCOME_FINAL = "OUTCOME_FINAL";

    public static final String EVENT_FLAGGED = "FLAGGED";
    public static final String EVENT_UNFLAGGED = "UNFLAGGED";
    public static final String EVENT_VERIFIED = "VERIFIED";
    public static final String EVENT_REOPENED = "REOPENED";
    public static final String EVENT_DECIDED = "DECIDED";
    public static final String EVENT_FAILED = "FAILED";
    public static final String EVENT_HELD = "HELD";
    public static final String EVENT_REJECTED = "REJECTED";
    public static final String BLOCKER_PENDING_VERIFICATION = "PENDING_VERIFICATION";
    public static final String BLOCKER_PROFILE_OFF = "PROFILE_OFF";
    public static final String BLOCKER_NO_ACTIVE_VERSION = "NO_ACTIVE_VERSION";
    public static final String BLOCKER_ENGINE_NOT_CONNECTED = "ENGINE_NOT_CONNECTED";

    /** The decision profile that covers a loan product, whatever state its versions are in. */
    @Getter
    @AllArgsConstructor
    public static class ProductProfileData {

        private final Long profileId;
        private final String profileName;
        /** OFF, SHADOW or LIVE */
        private final String mode;
        /** Version number of the live version, null when none is live yet. */
        private final Integer activeVersionNo;
    }

    @Getter
    @Builder
    public static class LoanDecisionData {

        private final Long loanId;
        private final Long productId;
        private final String productName;
        /** The product belongs to a decision profile, so the loan can be flagged. */
        private final boolean eligible;
        private final String profileName;
        private final String profileMode;
        private final Integer activeVersionNo;
        /** The application needs an automated credit decision. */
        private final boolean required;
        /** The application is still waiting for approval, so the flag can still be changed. */
        private final boolean canChangeFlag;
        /**
         * The application is in the stage where the automated credit decision is worked: verified, sent and read. Past it,
         * the decision can only be looked at.
         */
        private final boolean reviewStageOpen;
        /** A request can be sent, or sent again, in this stage: from the review until Due Diligence has been accepted. */
        private final boolean sendStageOpen;
        /** PENDING_VERIFICATION or VERIFIED */
        private final String verificationStatus;
        private final String verifiedBy;
        private final String verifiedOn;
        private final String verificationNote;
        /** NOT_SENT, DECIDED or FAILED */
        private final String requestStatus;
        private final String requestedBy;
        private final String requestedOn;
        private final String decision;
        private final BigDecimal score;
        private final String reasonCodes;
        /** What the engine reported besides the decision: the risk tier, the largest exposure it allows, and the model that scored. */
        private final String riskTier;
        private final BigDecimal maxExposure;
        private final String modelVersion;
        private final Integer replyMs;
        private final String errorMessage;
        private final String provider;
        /** The profile and version the request was built from, kept even if the profile has changed since. */
        private final String requestedProfileName;
        private final Integer requestedVersionNo;
        /** What was sent to the engine and what it replied, as sent and received. */
        private final String requestJson;
        private final String replyJson;
        /** Empty when the request can be sent. */
        private final List<String> blockers;
        /** Approval is refused until the engine has replied. */
        private final boolean blocksApproval;
        /**
         * Prepare and sign contract is refused: from Due Diligence only an AUTO_APPROVE goes straight to the contract. An application
         * that needs IC review gets there through the IC levels, and an ineligible one cannot get there.
         */
        private final boolean blocksContract;
        /** IC review is refused: only an IC_REVIEW_REQUIRED outcome goes through the IC levels. */
        private final boolean blocksIcReview;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FlagRequest {

        private boolean required;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class VerifyRequest {

        private String note;
    }

    /** One thing done to the decision of an application. */
    @Getter
    @Builder
    public static class EventData {

        private final Long id;
        /** FLAGGED, UNFLAGGED, VERIFIED, REOPENED, DECIDED, FAILED or HELD */
        private final String eventType;
        private final String actor;
        private final String occurredOn;
        private final String detail;
        private final String decision;
        private final BigDecimal score;
        /** The decision log row holding the request and the reply, for a request that was sent. */
        private final Long logId;
    }
}

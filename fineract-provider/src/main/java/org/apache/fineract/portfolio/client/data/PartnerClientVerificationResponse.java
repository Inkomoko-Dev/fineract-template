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
package org.apache.fineract.portfolio.client.data;

public class PartnerClientVerificationResponse {

    private boolean isRegistered;
    private String clientUid;
    private String verificationStatus;
    private String eligibilityStatus;
    private String remarks;

    protected PartnerClientVerificationResponse() {
        // for JSON deserialization
    }

    public PartnerClientVerificationResponse(final boolean isRegistered, final String clientUid, 
            final String verificationStatus, final String eligibilityStatus, final String remarks) {
        this.isRegistered = isRegistered;
        this.clientUid = clientUid;
        this.verificationStatus = verificationStatus;
        this.eligibilityStatus = eligibilityStatus;
        this.remarks = remarks;
    }

    public static PartnerClientVerificationResponse notRegistered() {
        return new PartnerClientVerificationResponse(false, null, "NOT_FOUND", "NOT_ELIGIBLE", 
                "Client not found in CBS");
    }

    public static PartnerClientVerificationResponse verifiedEligible(final String clientUid, final String remarks) {
        return new PartnerClientVerificationResponse(true, clientUid, "VERIFIED", "ELIGIBLE", remarks);
    }

    public static PartnerClientVerificationResponse verifiedIneligible(final String clientUid, final String remarks) {
        return new PartnerClientVerificationResponse(true, clientUid, "VERIFIED", "NOT_ELIGIBLE", remarks);
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    public String getClientUid() {
        return clientUid;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public String getEligibilityStatus() {
        return eligibilityStatus;
    }

    public String getRemarks() {
        return remarks;
    }
}
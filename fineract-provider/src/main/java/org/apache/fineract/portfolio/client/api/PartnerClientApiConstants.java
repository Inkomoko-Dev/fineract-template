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
package org.apache.fineract.portfolio.client.api;

public class PartnerClientApiConstants {

    private PartnerClientApiConstants() {
    }

    public static final String RESOURCE_NAME = "partnerclient";
    
    // Existing partner client mapping permissions
    public static final String READ_PARTNERCLIENT_CLIENT = "READ_PARTNERCLIENT";
    public static final String WRITE_PARTNERCLIENT_CLIENT = "WRITE_PARTNERCLIENT";
    public static final String ADMIN_READ_PARTNERCLIENT = "ADMIN_READ_PARTNERCLIENT";
    public static final String PERMISSION_CODE = READ_PARTNERCLIENT_CLIENT;
    
    // Query parameters for partner client listing
    public static final String PHONE = "phone";
    public static final String STATUS = "status";
    public static final String OFFICE_ID = "officeId";
    public static final String FROM_DATE = "fromDate";
    public static final String TO_DATE = "toDate";
    public static final String OFFSET = "offset";
    public static final String LIMIT = "limit";
    
    // Query parameters for admin operations
    public static final String PARTNER_CODE = "partnerCode";
    public static final String REASON = "reason";
    
    // Verification request parameters
    public static final String VERIFICATION_NATIONAL_ID = "nationalId";
    public static final String VERIFICATION_PHONE_NUMBER = "phoneNumber";
    public static final String VERIFICATION_FULL_NAME = "fullName";
    public static final String VERIFICATION_SOURCE_SYSTEM = "sourceSystem";
    
    // Verification response fields
    public static final String VERIFICATION_IS_REGISTERED = "isRegistered";
    public static final String VERIFICATION_CLIENT_UID = "clientUid";
    public static final String VERIFICATION_STATUS = "verificationStatus";
    public static final String VERIFICATION_ELIGIBILITY_STATUS = "eligibilityStatus";
    public static final String VERIFICATION_REMARKS = "remarks";
    
    // Verification statuses
    public static final String VERIFICATION_STATUS_VERIFIED = "VERIFIED";
    public static final String VERIFICATION_STATUS_NOT_FOUND = "NOT_FOUND";
    
    // Eligibility statuses
    public static final String ELIGIBILITY_STATUS_ELIGIBLE = "ELIGIBLE";
    public static final String ELIGIBILITY_STATUS_NOT_ELIGIBLE = "NOT_ELIGIBLE";
    
    // Verification permission
    public static final String VERIFY_PARTNER_CLIENT_PERMISSION = "VERIFY_PARTNER_CLIENT";
}
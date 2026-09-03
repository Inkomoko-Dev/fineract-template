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

import java.util.Objects;

public class PartnerClientVerificationRequest {

    private String nationalId;
    private String phoneNumber;
    private String fullName;
    private String sourceSystem;

    protected PartnerClientVerificationRequest() {
        // for JSON deserialization
    }

    public PartnerClientVerificationRequest(final String nationalId, final String phoneNumber, final String fullName, final String sourceSystem) {
        this.nationalId = nationalId;
        this.phoneNumber = phoneNumber;
        this.fullName = fullName;
        this.sourceSystem = sourceSystem;
    }

    public String getNationalId() {
        return nationalId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getFullName() {
        return fullName;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartnerClientVerificationRequest)) return false;
        PartnerClientVerificationRequest that = (PartnerClientVerificationRequest) o;
        return Objects.equals(nationalId, that.nationalId) &&
               Objects.equals(phoneNumber, that.phoneNumber) &&
               Objects.equals(fullName, that.fullName) &&
               Objects.equals(sourceSystem, that.sourceSystem);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nationalId, phoneNumber, fullName, sourceSystem);
    }

    @Override
    public String toString() {
        return "PartnerClientVerificationRequest{" +
                "nationalId='" + maskNationalId(nationalId) + '\'' +
                ", phoneNumber='" + maskPhoneNumber(phoneNumber) + '\'' +
                ", fullName='" + fullName + '\'' +
                ", sourceSystem='" + sourceSystem + '\'' +
                '}';
    }

    private String maskNationalId(String nationalId) {
        if (nationalId == null || nationalId.length() < 8) {
            return "XXXX";
        }
        return nationalId.substring(0, 4) + "XXXXXXXX" + nationalId.substring(nationalId.length() - 4);
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "XXXX";
        }
        return phoneNumber.substring(0, phoneNumber.length() - 4) + "XXXX";
    }
}
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

import lombok.Data;

@Data
public class TransUnionRwandaConsumerCreditData {

    private transient Long selectedAddressId;
    private transient String selectedAddressType;
    private Integer loanId;
    private Integer loanStatus;
    private String currencyType;
    private String country;
    private String drivingLicenseNumber;
    private String occupation;
    private String surName;
    private String studentNumber;
    private String postalCode;
    private String dateAccountUpdated;
    private String physicalAddressPlotNumber;
    private Integer daysInArrears;
    private String residenceType;
    private String emailAddress;
    private String foreName3;
    private String foreName2;
    private String foreName1;
    private Integer openingBalance;
    private String passportNumber;
    private String sectorOfActivity;
    private String oldAccountNumber;
    private Integer availableCredit;
    private String facsimile;
    private String accountRepaymentTerm;
    private String accountType;
    private Integer currentBalance;
    private String taxNumber;
    private String accountOwner;
    private Integer classification;
    private String physicalAddressDistrict;
    private String employerAddressLine2;
    private String employerAddressLine1;
    private String incomeFrequency;
    private String healthInsuranceNumber;
    private Integer scheduledPaymentAmount;
    private String groupName;
    private String mobileTelephone;
    private Integer actualPaymentAmount;
    private String nationality;
    private String dateAccountOpened;
    private String salutation;
    private Double interestRateAtDisbursement;
    private Integer noOfDependants;
    private String physicalAddressProvince;
    private String maritalStatus;
    private String postalAddressNumber;
    private String currentBalanceIndicator;
    private Integer income;
    private String approvalDate;
    private String gender;
    private String socialSecurityNumber;
    private String physicalAddressSector;
    private String employerName;
    private String firstPaymentDate;
    private String dateClosed;
    private String accountStatus;
    private Integer numberOfJointLoanParticipants;
    private Integer termsDuration;
    private String lastPaymentDate;
    private String physicalAddressCell;
    private String homeTelephone;
    private String employerTown;
    private String physicalAddressLine2;
    private String physicalAddressLine1;
    private String employerCountry;
    private String placeOfBirth;
    private String physicalAddressPostalCode;
    private Integer nature;
    private String dateOfBirth;
    private String workTelephone;
    private String accountNumber;
    private String finalPaymentDate;
    private Long groupNumber;
    private Integer installmentsInArrears;
    private String nationalId;
    private Integer amountPastDue;
    private Integer category;
}

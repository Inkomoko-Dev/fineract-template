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
package org.apache.fineract.infrastructure.creditbureau.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CrbPostingLogReportData {

    private  Long loanId;
    private  String batchId;
    private String loanAccountNumber;
    private  Boolean posted;
    private  LocalDate datePosted;
    private  String errorLogs;
    private  String clientType;
    private  Integer daysInArrears;
    private  LocalDate lastPaymentDate;


    public CrbPostingLogReportData(Long loanId, String batchId,String loanAccountNumber, Boolean posted, LocalDate datePosted, String errorLogs, String clientType, Integer daysInArrears, LocalDate lastPaymentDate) {
        this.loanId = loanId;
        this.batchId = batchId;
        this.loanAccountNumber = loanAccountNumber;
        this.posted = posted;
        this.datePosted = datePosted;
        this.errorLogs = errorLogs;
        this.clientType = clientType;
        this.daysInArrears = daysInArrears;
        this.lastPaymentDate = lastPaymentDate;
    }
}


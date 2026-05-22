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
package org.apache.fineract.portfolio.loanaccount.domain;

import java.io.Serial;
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Getter
@Setter
@Entity
@Table(name = "m_kiva_posting_logger")
public class KivaPostingLogger extends AbstractAuditableWithUTCDateTimeCustom {

    @Serial
    private static final long serialVersionUID = 5380842322172779067L;

    @Column(name = "batch_id")
    private String batchId;

    @Column(name = "has_passed")
    private Boolean hasPassed;

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "loan_account_no")
    private String loanAccountNo;

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "failure_stage")
    private String failureStage;

    @Column(name = "failure_category")
    private String failureCategory;

    @Column(name = "kiva_response_id")
    private String kivaResponseId;

    @Column(name = "error_logs")
    private String errorLogs;

    @Column(name = "pay_load")
    private String payload;

    @Column(name = "date")
    private LocalDate date;

    public KivaPostingLogger() {}

    public KivaPostingLogger(String batchId, Boolean hasPassed, Long loanId, String loanAccountNo, Long clientId, String clientName,
            String failureStage, String failureCategory, String kivaResponseId, String errorLogs, String payload, LocalDate date) {
        this.batchId = batchId;
        this.hasPassed = hasPassed;
        this.loanId = loanId;
        this.loanAccountNo = loanAccountNo;
        this.clientId = clientId;
        this.clientName = clientName;
        this.failureStage = failureStage;
        this.failureCategory = failureCategory;
        this.kivaResponseId = kivaResponseId;
        this.errorLogs = errorLogs;
        this.payload = payload;
        this.date = date;
    }
}

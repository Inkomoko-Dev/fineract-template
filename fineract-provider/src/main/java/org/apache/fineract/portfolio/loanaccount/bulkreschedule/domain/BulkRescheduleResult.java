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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_bulk_reschedule_result", indexes = {
    @Index(name = "idx_bulk_reschedule_result_execution_id", columnList = "bulk_reschedule_execution_id"),
    @Index(name = "idx_bulk_reschedule_result_loan_id", columnList = "loan_id"),
    @Index(name = "idx_bulk_reschedule_result_status", columnList = "status_enum")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRescheduleResult  extends AbstractPersistableCustom {


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bulk_reschedule_execution_id", nullable = false)
    private BulkRescheduleExecution execution;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "loan_account_number", length = 100)
    private String loanAccountNumber;

    @Column(name = "client_name", length = 255)
    private String clientName;

    @Column(name = "account_number", length = 100)
    private String accountNumber;

    @Column(name = "office_id_snapshot")
    private Long officeId;

    @Column(name = "office_name", length = 255)
    private String officeName;

    @Column(name = "loan_product_name", length = 255)
    private String loanProductName;

    @Column(name = "loan_officer_id_snapshot")
    private Long loanOfficerId;

    @Column(name = "loan_officer_name", length = 255)
    private String loanOfficerName;

    @Column(name = "loan_status", length = 100)
    private String loanStatus;

    @Column(name = "status_enum", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private BulkRescheduleResultStatus status;

    @Column(name = "original_interest_rate", precision = 19, scale = 6)
    private BigDecimal originalInterestRate;

    @Column(name = "new_interest_rate", precision = 19, scale = 6)
    private BigDecimal newInterestRate;

    @Column(name = "interest_rate_method", length = 100)
    private String interestRateMethod;

    @Column(name = "total_outstanding", precision = 19, scale = 6)
    private BigDecimal totalOutstanding;

    @Column(name = "new_total_outstanding", precision = 19, scale = 6)
    private BigDecimal newTotalOutstanding;

    @Column(name = "current_term")
    private Integer currentTerm;

    @Column(name = "new_term")
    private Integer newTerm;

    @Column(name = "next_scheduled_installment")
    private java.time.LocalDate nextScheduledInstallment;

    @Column(name = "reschedule_reason", length = 255)
    private String rescheduleReason;

    @Column(name = "original_reschedule_request_id")
    private Long originalRescheduleRequestId;

    @Column(name = "reschedule_request_id")
    private Long rescheduleRequestId;

    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(name = "exclude_reason", length = 255)
    private String excludeReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum BulkRescheduleResultStatus {
        PREVIEW_MATCHED,
        SUCCEEDED,
        FAILED,
        EXCLUDED,
        SKIPPED,
        ROLLED_BACK,
        ROLLBACK_FAILED
    }
}

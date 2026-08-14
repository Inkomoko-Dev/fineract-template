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
import org.apache.fineract.useradministration.domain.AppUser;

@Entity
@Table(name = "m_bulk_reschedule_execution", indexes = {
    @Index(name = "idx_bulk_reschedule_execution_user_id", columnList = "user_id"),
    @Index(name = "idx_bulk_reschedule_execution_office_id", columnList = "office_id"),
    @Index(name = "idx_bulk_reschedule_execution_status", columnList = "status_enum"),
    @Index(name = "idx_bulk_reschedule_execution_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRescheduleExecution extends AbstractPersistableCustom {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "status_enum", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private BulkRescheduleExecutionStatus status;

    @Column(name = "mode_enum", nullable = false)
    @Enumerated(EnumType.ORDINAL)
    private BulkRescheduleMode mode;

    @Column(name = "filters_json", columnDefinition = "LONGTEXT")
    private String filtersJson;

    @Column(name = "rescheduling_details_json", columnDefinition = "LONGTEXT")
    private String reschedulingDetailsJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id", nullable = true)
    private AppUser approver;

    @Column(name = "approval_note", columnDefinition = "LONGTEXT")
    private String approvalNote;

    @Column(name = "submission_note", columnDefinition = "LONGTEXT")
    private String submissionNote;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "execution_error", columnDefinition = "LONGTEXT")
    private String executionError;

    @Column(name = "execution_started_at")
    private LocalDateTime executionStartedAt;

    @Column(name = "execution_completed_at")
    private LocalDateTime executionCompletedAt;

    @Column(name = "total_loans_found", nullable = false)
    private Integer totalLoansFound = 0;

    @Column(name = "total_succeeded", nullable = false)
    private Integer totalSucceeded = 0;

    @Column(name = "total_failed", nullable = false)
    private Integer totalFailed = 0;

    @Column(name = "total_execution_failed", nullable = false)
    private Integer totalExecutionFailed = 0;

    @Column(name = "total_excluded", nullable = false)
    private Integer totalExcluded = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "worker_token", length = 64)
    private String workerToken;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "last_heartbeat_at")
    private LocalDateTime lastHeartbeatAt;

    public enum BulkRescheduleExecutionStatus {
        PREVIEW,
        PENDING_APPROVAL,
        APPROVED,
        EXECUTING,
        COMPLETED,
        PARTIAL_SUCCESS,
        REJECTED,
        ROLLING_BACK,
        ROLLED_BACK,
        FAILED
    }

    public enum BulkRescheduleMode {
        DRY_RUN,
        EXECUTE
    }
}

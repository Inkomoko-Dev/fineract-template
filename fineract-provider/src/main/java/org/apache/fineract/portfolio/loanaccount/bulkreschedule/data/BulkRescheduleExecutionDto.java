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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.Builder;
import lombok.Data;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;

@Data
@Builder
public class BulkRescheduleExecutionDto {

    private Long id;
    private Long officeId;
    private String officeName;
    private String status;
    private String mode;
    private Long createdById;
    private String createdByUsername;
    private Long approverId;
    private String approverUsername;
    private String approverDisplayName;
    private String submissionNote;
    private String approvalNote;
    private String rejectionReason;
    private Integer totalLoansFound;
    private Integer totalSucceeded;
    private Integer totalFailed;
    private Integer totalExecutionFailed;
    private Integer totalExcluded;
    private String approvedAt;
    private String submittedAt;
    private String executionError;
    private String executionStartedAt;
    private String executionCompletedAt;
    private Integer totalProcessed;
    private Integer totalRemaining;
    private String createdAt;
    private String updatedAt;

    private static String isoDateTime(final LocalDateTime value) {
        return value == null ? null : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
    }

    public static BulkRescheduleExecutionDto toExecutionDto(final BulkRescheduleExecution execution, final String officeName) {

        return BulkRescheduleExecutionDto.builder()
                .id(execution.getId())
                .officeId(execution.getOfficeId())
                .officeName(officeName)
                .status(execution.getStatus() != null ? execution.getStatus().name() : null)
                .mode(execution.getMode() != null ? execution.getMode().name() : null)
                .createdById(execution.getUser() != null ? execution.getUser().getId() : null)
                .createdByUsername(execution.getUser() != null ? execution.getUser().getUsername() : null)
                .approverId(execution.getApprover() != null ? execution.getApprover().getId() : null)
                .approverUsername(execution.getApprover() != null ? execution.getApprover().getUsername() : null)
                .approverDisplayName(execution.getApprover() != null ? execution.getApprover().getDisplayName() : null)
                .submissionNote(execution.getSubmissionNote())
                .approvalNote(execution.getStatus() != BulkRescheduleExecution.BulkRescheduleExecutionStatus.REJECTED
                        ? execution.getApprovalNote() : null)
                .rejectionReason(execution.getStatus() == BulkRescheduleExecution.BulkRescheduleExecutionStatus.REJECTED
                        ? execution.getApprovalNote() : null)
                .totalLoansFound(execution.getTotalLoansFound())
                .totalSucceeded(execution.getTotalSucceeded())
                .totalFailed(execution.getTotalFailed())
                .totalExecutionFailed(execution.getTotalExecutionFailed())
                .totalExcluded(execution.getTotalExcluded())
                .approvedAt(isoDateTime(execution.getApprovedAt()))
                .submittedAt(isoDateTime(execution.getSubmittedAt()))
                .executionError(execution.getExecutionError())
                .executionStartedAt(isoDateTime(execution.getExecutionStartedAt()))
                .executionCompletedAt(isoDateTime(execution.getExecutionCompletedAt()))
                .totalProcessed(value(execution.getTotalSucceeded()) + value(execution.getTotalExecutionFailed()))
                .totalRemaining(Math.max(0, value(execution.getTotalLoansFound()) - value(execution.getTotalExcluded())
                        - value(execution.getTotalFailed()) - value(execution.getTotalSucceeded())))
                .createdAt(isoDateTime(execution.getCreatedAt()))
                .updatedAt(isoDateTime(execution.getUpdatedAt()))
                .build();
    }

    private static int value(final Integer number) {
        return number == null ? 0 : number;
    }
}

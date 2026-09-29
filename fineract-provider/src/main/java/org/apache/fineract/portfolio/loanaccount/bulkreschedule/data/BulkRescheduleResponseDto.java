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

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for bulk reschedule response. Contains execution details, mode, status, and results of a
 * bulk reschedule operation. Includes preview data for dry runs and execution results for actual executions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRescheduleResponseDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Unique execution ID (UUID) for tracking this bulk reschedule operation */
    private Long executionId;

    /** Mode of execution (DRY_RUN or EXECUTE) */
    private String mode;

    /** Current status of the execution */
    private String status;

    /** Status message providing details about the execution */
    private String message;

    /** Number of loans successfully rescheduled */
    private Integer totalSucceeded;

    /** Number of loans that failed to reschedule */
    private Integer totalFailed;

    /** Number of loans excluded from reschedule */
    private Integer totalExcluded;

    /** List of successfully rescheduled loans */
    private List<BulkRescheduleSuccessDto> succeeded;

    /** List of loans that failed reschedule */
    private List<BulkRescheduleFailedDto> failed;
}

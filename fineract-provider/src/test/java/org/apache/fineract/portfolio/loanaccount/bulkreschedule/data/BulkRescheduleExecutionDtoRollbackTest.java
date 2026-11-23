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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleMode;
import org.junit.jupiter.api.Test;

class BulkRescheduleExecutionDtoRollbackTest {

    @Test
    void partialSuccessIsRollbackAvailableEvenWhenSkippedWouldSkewStoredCounts() {
        final BulkRescheduleExecution execution = new BulkRescheduleExecution();
        execution.setStatus(BulkRescheduleExecutionStatus.PARTIAL_SUCCESS);
        execution.setMode(BulkRescheduleMode.EXECUTE);
        execution.setTotalLoansFound(10);
        execution.setTotalExcluded(0);
        execution.setTotalFailed(1);
        execution.setTotalSucceeded(3);
        // 6 SKIPPED are not stored on the header; remaining calc used to look unfinished.

        final BulkRescheduleExecutionDto dto = BulkRescheduleExecutionDto.toExecutionDto(execution, "South Sudan");

        assertThat(dto.getRollbackAvailable()).isTrue();
    }

    @Test
    void completedIsRollbackAvailable() {
        final BulkRescheduleExecution execution = new BulkRescheduleExecution();
        execution.setStatus(BulkRescheduleExecutionStatus.COMPLETED);
        execution.setMode(BulkRescheduleMode.EXECUTE);
        execution.setTotalLoansFound(5);
        execution.setTotalExcluded(0);
        execution.setTotalFailed(0);
        execution.setTotalSucceeded(5);

        final BulkRescheduleExecutionDto dto = BulkRescheduleExecutionDto.toExecutionDto(execution, "Kenya");

        assertThat(dto.getRollbackAvailable()).isTrue();
    }
}

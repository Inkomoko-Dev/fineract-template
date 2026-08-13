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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BulkRescheduleResultRepository extends JpaRepository<BulkRescheduleResult, Long> {

    void deleteByExecutionId(Long executionId);

    @Query("SELECT r FROM BulkRescheduleResult r WHERE r.execution.id = :executionId ORDER BY r.createdAt DESC")
    Page<BulkRescheduleResult> findPageByExecutionId(@Param("executionId") Long executionId, Pageable pageable);

    @Query("SELECT r FROM BulkRescheduleResult r WHERE r.execution.id = :executionId "
            + "AND r.status = :status ORDER BY r.id")
    Page<BulkRescheduleResult> findPageByExecutionIdAndStatus(@Param("executionId") Long executionId,
            @Param("status") BulkRescheduleResultStatus status, Pageable pageable);

    @Query("SELECT COUNT(r) FROM BulkRescheduleResult r WHERE r.execution.id = :executionId AND r.status = :status")
    long countByExecutionIdAndStatus(@Param("executionId") Long executionId,
            @Param("status") BulkRescheduleResultStatus status);

    /**
     * Find all results for a specific execution
     */
    List<BulkRescheduleResult> findByExecutionId(String executionId);

    /**
     * Find all results for a specific execution with pagination support
     */
    @Query("""
        SELECT r
        FROM BulkRescheduleResult r
        WHERE r.execution.id = :executionId
        ORDER BY r.createdAt DESC
        """)
    List<BulkRescheduleResult> findByExecutionIdOrdered(
            @Param("executionId") String executionId);

    /**
     * Find results for a specific execution and status
     */
    @Query("""
        SELECT r
        FROM BulkRescheduleResult r
        WHERE r.execution.id = :executionId
          AND r.status = :status
        ORDER BY r.createdAt DESC
        """)
    List<BulkRescheduleResult> findByExecutionAndStatus(
            @Param("executionId") String executionId,
            @Param("status") BulkRescheduleResultStatus status);

    /**
     * Find all results with the given status for an execution
     */
    @Query("""
        SELECT r
        FROM BulkRescheduleResult r
        WHERE r.execution.id = :executionId
          AND r.status = :status
        ORDER BY r.createdAt DESC
        """)
    List<BulkRescheduleResult> findResultsByStatus(
            @Param("executionId") String executionId,
            @Param("status") BulkRescheduleResultStatus status);

    /**
     * Find result for a specific loan in an execution
     */
    @Query("""
        SELECT r
        FROM BulkRescheduleResult r
        WHERE r.execution.id = :executionId
          AND r.loanId = :loanId
        """)
    Optional<BulkRescheduleResult> findByExecutionAndLoan(
            @Param("executionId") String executionId,
            @Param("loanId") Long loanId);

    /**
     * Count results by status for an execution
     */
    @Query("""
        SELECT COUNT(r)
        FROM BulkRescheduleResult r
        WHERE r.execution.id = :executionId
          AND r.status = :status
        """)
    long countByExecutionAndStatus(
            @Param("executionId") String executionId,
            @Param("status") BulkRescheduleResultStatus status);

    /**
     * Find result by reschedule request ID (for rollback)
     */
    @Query("""
        SELECT r
        FROM BulkRescheduleResult r
        WHERE r.rescheduleRequestId = :rescheduleRequestId
        """)
    Optional<BulkRescheduleResult> findByRescheduleRequestId(
            @Param("rescheduleRequestId") Long rescheduleRequestId);

    /**
     * Find results with non-null reschedule request IDs for an execution
     */
    @Query("""
        SELECT r
        FROM BulkRescheduleResult r
        WHERE r.execution.id = :executionId
          AND r.rescheduleRequestId IS NOT NULL
        """)
    List<BulkRescheduleResult> findRollbackableResults(
            @Param("executionId") String executionId);

    /**
     * Find all results for a specific execution entity
     */
    @Query("""
        SELECT r
        FROM BulkRescheduleResult r
        WHERE r.execution = :execution
        ORDER BY r.createdAt DESC
        """)
    List<BulkRescheduleResult> findByExecution(
            @Param("execution") BulkRescheduleExecution execution);

    /**
     * Find results for a specific execution entity and status
     */
    @Query("""
        SELECT r
        FROM BulkRescheduleResult r
        WHERE r.execution = :execution
          AND r.status = :status
        ORDER BY r.createdAt DESC
        """)
    List<BulkRescheduleResult> findByExecutionAndStatus(
            @Param("execution") BulkRescheduleExecution execution,
            @Param("status") BulkRescheduleResultStatus status);
}

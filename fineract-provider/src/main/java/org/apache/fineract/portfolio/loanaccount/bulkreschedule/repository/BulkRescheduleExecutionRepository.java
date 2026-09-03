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

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution.BulkRescheduleExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface BulkRescheduleExecutionRepository
        extends JpaRepository<BulkRescheduleExecution, Long>,
        JpaSpecificationExecutor<BulkRescheduleExecution> {

    @Modifying
    @Query("""
        UPDATE BulkRescheduleExecution e
        SET e.status = :executing,
            e.executionStartedAt = CURRENT_TIMESTAMP,
            e.workerToken = :workerToken,
            e.leaseExpiresAt = :leaseExpiresAt,
            e.lastHeartbeatAt = CURRENT_TIMESTAMP,
            e.updatedAt = CURRENT_TIMESTAMP
        WHERE e.id = :executionId
          AND e.status = :approved
        """)
    int claimApproved(
            @Param("executionId") Long executionId,
            @Param("approved") BulkRescheduleExecutionStatus approved,
            @Param("executing") BulkRescheduleExecutionStatus executing,
            @Param("workerToken") String workerToken,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Modifying
    @Query("""
        UPDATE BulkRescheduleExecution e
        SET e.workerToken = :workerToken,
            e.leaseExpiresAt = :leaseExpiresAt,
            e.lastHeartbeatAt = CURRENT_TIMESTAMP,
            e.updatedAt = CURRENT_TIMESTAMP
        WHERE e.id = :executionId
          AND e.status = :executing
          AND (e.leaseExpiresAt IS NULL OR e.leaseExpiresAt < :now)
        """)
    int claimExpired(@Param("executionId") Long executionId,
            @Param("executing") BulkRescheduleExecutionStatus executing,
            @Param("workerToken") String workerToken,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt,
            @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        UPDATE BulkRescheduleExecution e
        SET e.leaseExpiresAt = :leaseExpiresAt,
            e.lastHeartbeatAt = CURRENT_TIMESTAMP,
            e.updatedAt = CURRENT_TIMESTAMP
        WHERE e.id = :executionId
          AND e.status = :executing
          AND e.workerToken = :workerToken
        """)
    int renewLease(@Param("executionId") Long executionId,
            @Param("executing") BulkRescheduleExecutionStatus executing,
            @Param("workerToken") String workerToken,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    /**
     * Find all executions for a specific user.
     */
    List<BulkRescheduleExecution> findByUserId(Long userId);

    /**
     * Find all executions for a specific office.
     */
    List<BulkRescheduleExecution> findByOfficeId(Long officeId);

    /**
     * Find all executions by status.
     */
    List<BulkRescheduleExecution> findByStatus(
            BulkRescheduleExecutionStatus status);

    /**
     * Find all executions within a date range.
     */
    @Query("""
        SELECT e
        FROM BulkRescheduleExecution e
        WHERE e.createdAt BETWEEN :startDate AND :endDate
        ORDER BY e.createdAt DESC
        """)
    List<BulkRescheduleExecution> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find executions by office and status.
     */
    @Query("""
        SELECT e
        FROM BulkRescheduleExecution e
        WHERE e.officeId = :officeId
          AND e.status = :status
        ORDER BY e.createdAt DESC
        """)
    List<BulkRescheduleExecution> findByOfficeAndStatus(
            @Param("officeId") Long officeId,
            @Param("status") BulkRescheduleExecutionStatus status);

    /**
     * Find executions by user and status.
     */
    @Query("""
        SELECT e
        FROM BulkRescheduleExecution e
        WHERE e.user.id = :userId
          AND e.status = :status
        ORDER BY e.createdAt DESC
        """)
    List<BulkRescheduleExecution> findByUserAndStatus(
            @Param("userId") Long userId,
            @Param("status") BulkRescheduleExecutionStatus status);

    /**
     * Find execution by ID.
     */
    @Override
    Optional<BulkRescheduleExecution> findById(Long executionId);

    /**
     * Find approved/executing/completed executions.
     */
    @Query("""
        SELECT e
        FROM BulkRescheduleExecution e
        WHERE e.status IN :statuses
        ORDER BY e.createdAt DESC
        """)
    List<BulkRescheduleExecution> findApprovedExecutions(
            @Param("statuses")
            Collection<BulkRescheduleExecutionStatus> statuses);

    /**
     * Find completed executions that can be rolled back.
     */
    @Query("""
        SELECT e
        FROM BulkRescheduleExecution e
        WHERE e.status IN :statuses
        ORDER BY e.updatedAt DESC
        """)
    List<BulkRescheduleExecution> findCompletedExecutions(
            @Param("statuses")
            Collection<BulkRescheduleExecutionStatus> statuses);

    /**
     * Find pending approval executions for a specific office.
     */
    @Query("""
        SELECT e
        FROM BulkRescheduleExecution e
        WHERE e.officeId = :officeId
          AND e.status = :status
        ORDER BY e.createdAt DESC
        """)
    List<BulkRescheduleExecution> findPendingApprovalByOffice(
            @Param("officeId") Long officeId,
            @Param("status") BulkRescheduleExecutionStatus status);

    /**
     * Find executions by user within a date range.
     */
    @Query("""
        SELECT e
        FROM BulkRescheduleExecution e
        WHERE e.user.id = :userId
          AND e.createdAt BETWEEN :startDate AND :endDate
        ORDER BY e.createdAt DESC
        """)
    List<BulkRescheduleExecution> findByUserAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}

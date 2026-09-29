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
import java.util.List;
import java.util.Collection;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleAudit;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleAudit.BulkRescheduleAuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BulkRescheduleAuditRepository extends JpaRepository<BulkRescheduleAudit, Long> {

    void deleteByExecutionId(Long executionId);

    /**
     * Find all audit entries for a specific execution
     */
    List<BulkRescheduleAudit> findByExecutionId(Long executionId);

    /**
     * Find all audit entries for a specific execution ordered by timestamp
     */
    @Query("""
        SELECT a
        FROM BulkRescheduleAudit a
        WHERE a.execution.id = :executionId
        ORDER BY a.timestamp ASC
        """)
    List<BulkRescheduleAudit> findByExecutionIdOrdered(
            @Param("executionId") Long executionId);

    /**
     * Find audit entries for a specific execution and action
     */
    @Query("""
        SELECT a
        FROM BulkRescheduleAudit a
        WHERE a.execution.id = :executionId
          AND a.action = :action
        ORDER BY a.timestamp DESC
        """)
    List<BulkRescheduleAudit> findByExecutionAndAction(
            @Param("executionId") Long executionId,
            @Param("action") BulkRescheduleAuditAction action);

    /**
     * Find audit entries for a specific actor
     */
    @Query("""
        SELECT a
        FROM BulkRescheduleAudit a
        WHERE a.actor.id = :actorId
        ORDER BY a.timestamp DESC
        """)
    List<BulkRescheduleAudit> findByActorId(
            @Param("actorId") Long actorId);

    /**
     * Find audit entries within a date range
     */
    @Query("""
        SELECT a
        FROM BulkRescheduleAudit a
        WHERE a.timestamp BETWEEN :startDate AND :endDate
        ORDER BY a.timestamp DESC
        """)
    List<BulkRescheduleAudit> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find audit entries for a specific execution within a date range
     */
    @Query("""
        SELECT a
        FROM BulkRescheduleAudit a
        WHERE a.execution.id = :executionId
          AND a.timestamp BETWEEN :startDate AND :endDate
        ORDER BY a.timestamp ASC
        """)
    List<BulkRescheduleAudit> findByExecutionAndDateRange(
            @Param("executionId") Long executionId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find the latest audit entry for an execution
     */
    BulkRescheduleAudit findFirstByExecutionIdOrderByTimestampDesc(Long executionId);

    /**
     * Count audit entries for a specific action
     */
    @Query("""
        SELECT COUNT(a)
        FROM BulkRescheduleAudit a
        WHERE a.execution.id = :executionId
          AND a.action = :action
        """)
    long countByExecutionAndAction(
            @Param("executionId") Long executionId,
            @Param("action") BulkRescheduleAuditAction action);

    /**
     * Find approval history
     */
    @Query("""
        SELECT a
        FROM BulkRescheduleAudit a
        WHERE a.execution.id = :executionId
          AND a.action IN :actions
        ORDER BY a.timestamp DESC
        """)
    List<BulkRescheduleAudit> findApprovalHistory(
            @Param("executionId") Long executionId,
            @Param("actions") Collection<BulkRescheduleAuditAction> actions);

    /**
     * Find execution history
     */
    @Query("""
        SELECT a
        FROM BulkRescheduleAudit a
        WHERE a.execution.id = :executionId
          AND a.action IN :actions
        ORDER BY a.timestamp DESC
        """)
    List<BulkRescheduleAudit> findExecutionHistory(
            @Param("executionId") Long executionId,
            @Param("actions") Collection<BulkRescheduleAuditAction> actions);
}

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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanHistoricalPenaltyWaiverRepository extends JpaRepository<LoanHistoricalPenaltyWaiver, Long> {

    List<LoanHistoricalPenaltyWaiver> findByLoanIdOrderByIdDesc(Long loanId);

    List<LoanHistoricalPenaltyWaiver> findByStatusOrderByIdAsc(HistoricalPenaltyWaiverStatus status);

    Optional<LoanHistoricalPenaltyWaiver> findFirstByLoanChargeIdAndStatusOrderByIdAsc(Long loanChargeId,
            HistoricalPenaltyWaiverStatus status);

    @Query("select w from LoanHistoricalPenaltyWaiver w where w.status = :status and w.escalatedOnDate is null")
    List<LoanHistoricalPenaltyWaiver> findNotYetEscalated(@Param("status") HistoricalPenaltyWaiverStatus status);

    /**
     * Completes a waiver once Odoo holds every journal entry it touched. Native because it joins the ledger, and set
     * based so the nightly job stays one statement however many waivers are outstanding.
     */
    @Modifying
    @Query(value = """
            update m_loan_historical_penalty_waiver a
               set a.status = 'COMPLETED', a.odoo_sync_completed_on_date = :syncedOn
             where a.status = 'PENDING_ODOO_SYNC'
               and not exists (select 1 from m_loan_historical_penalty_waiver_txn t
                                 join acc_gl_journal_entry gl on gl.loan_transaction_id = t.loan_transaction_id
                                where t.waiver_id = a.id and gl.is_oddo_posted = false)
            """, nativeQuery = true)
    int completeWaiversFullyPostedToOdoo(@Param("syncedOn") OffsetDateTime syncedOn);
}

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

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanTransactionRepository extends JpaRepository<LoanTransaction, Long>, JpaSpecificationExecutor<LoanTransaction> {

    @Query("select tx from LoanTransaction tx where tx.loan.id = :loanId AND tx.typeOf = 2 ORDER BY tx.id DESC")
    List<LoanTransaction> findLastLoanTransaction(@Param("loanId") Long loanId);

    @Query("""
            select tx from LoanTransaction tx
            where tx.loan.id = :loanId
                and tx.typeOf = :#{#type.value}
                and tx.reversed = false
                and tx.reversalTransaction = false
            order by tx.id desc
            """)
    List<LoanTransaction> findTransactionsByLoanAndType(@Param("loanId") Long loanId, @Param("type") LoanTransactionType type);

    @Query("""
            select case when count(tx) > 0 then true else false end
            from LoanTransaction tx
            where tx.originalTxnId = :originalTransactionId
                and tx.reversalTransaction = false
                and tx.reversed = false
            """)
    boolean existsActiveCorrectedRecoveryTransaction(@Param("originalTransactionId") Long originalTransactionId);

    @Query("""
            select case when count(tx) > 0 then true else false end
            from LoanTransaction tx
            where tx.loan.id = :loanId
                and tx.typeOf = :#{#type.value}
                and tx.dateOf = :transactionDate
                and tx.reversed = false
                and tx.reversalTransaction = false
            """)
    boolean existsNonReversedAccrualForLoanAndDate(@Param("loanId") Long loanId, @Param("type") LoanTransactionType type,
            @Param("transactionDate") LocalDate transactionDate);
}

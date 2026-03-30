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

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanDecisionLevelRepository extends JpaRepository<LoanDecisionLevel, Long> {

    @Query("SELECT ldl FROM LoanDecisionLevel ldl WHERE ldl.loanDecision.id = :loanDecisionId ORDER BY ldl.levelNumber ASC")
    List<LoanDecisionLevel> findByLoanDecisionIdOrderByLevelNumber(@Param("loanDecisionId") Long loanDecisionId);

    @Query("SELECT ldl FROM LoanDecisionLevel ldl WHERE ldl.loanDecision.id = :loanDecisionId AND ldl.levelNumber = :levelNumber")
    LoanDecisionLevel findByLoanDecisionIdAndLevelNumber(@Param("loanDecisionId") Long loanDecisionId,
                                                           @Param("levelNumber") Integer levelNumber);

    @Query("SELECT ldl FROM LoanDecisionLevel ldl WHERE ldl.loanDecision.id = :loanDecisionId AND ldl.icReviewLevel.id = :icReviewLevelId")
    LoanDecisionLevel findByLoanDecisionIdAndIcReviewLevelId(@Param("loanDecisionId") Long loanDecisionId,
                                                               @Param("icReviewLevelId") Long icReviewLevelId);

    @Query("SELECT ldl FROM LoanDecisionLevel ldl WHERE ldl.loanDecision.loan.id = :loanId ORDER BY ldl.levelNumber ASC")
    List<LoanDecisionLevel> findByLoanIdOrderByLevelNumber(@Param("loanId") Long loanId);

    @Query("SELECT ldl FROM LoanDecisionLevel ldl WHERE ldl.loanDecision.loan.id = :loanId AND ldl.levelNumber = :levelNumber")
    LoanDecisionLevel findByLoanIdAndLevelNumber(@Param("loanId") Long loanId, @Param("levelNumber") Integer levelNumber);

    @Query("SELECT ldl FROM LoanDecisionLevel ldl WHERE ldl.loanDecision.loan.id = :loanId AND ldl.isSigned = true ORDER BY ldl.levelNumber DESC")
    List<LoanDecisionLevel> findSignedLevelsByLoanIdOrderByLevelNumberDesc(@Param("loanId") Long loanId);

    @Query("DELETE FROM LoanDecisionLevel ldl WHERE ldl.loanDecision.id = :loanDecisionId")
    void deleteByLoanDecisionId(@Param("loanDecisionId") Long loanDecisionId);
}

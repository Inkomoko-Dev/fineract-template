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

public interface LoanApprovalMatrixLevelRepository extends JpaRepository<LoanApprovalMatrixLevel, Long> {

    @Query("SELECT aml FROM LoanApprovalMatrixLevel aml WHERE aml.approvalMatrix.id = :matrixId ORDER BY aml.levelNumber ASC")
    List<LoanApprovalMatrixLevel> findByApprovalMatrixIdOrderByLevelNumber(@Param("matrixId") Long matrixId);

    @Query("SELECT aml FROM LoanApprovalMatrixLevel aml WHERE aml.approvalMatrix.id = :matrixId AND aml.levelNumber = :levelNumber")
    LoanApprovalMatrixLevel findByApprovalMatrixIdAndLevelNumber(@Param("matrixId") Long matrixId,
                                                                   @Param("levelNumber") Integer levelNumber);

    @Query("SELECT aml FROM LoanApprovalMatrixLevel aml WHERE aml.approvalMatrix.id = :matrixId AND aml.icReviewLevel.id = :icReviewLevelId")
    LoanApprovalMatrixLevel findByApprovalMatrixIdAndIcReviewLevelId(@Param("matrixId") Long matrixId,
                                                                       @Param("icReviewLevelId") Long icReviewLevelId);

    @Query("DELETE FROM LoanApprovalMatrixLevel aml WHERE aml.approvalMatrix.id = :matrixId")
    void deleteByApprovalMatrixId(@Param("matrixId") Long matrixId);
}

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

public interface IcReviewLevelConfigRepository extends JpaRepository<IcReviewLevelConfig, Long> {

    @Query("SELECT ic FROM IcReviewLevelConfig ic WHERE ic.isActive = true ORDER BY ic.displayOrder ASC")
    List<IcReviewLevelConfig> findAllActiveOrderByDisplayOrder();

    @Query("SELECT ic FROM IcReviewLevelConfig ic WHERE ic.levelNumber = :levelNumber AND ic.isActive = true")
    IcReviewLevelConfig findByLevelNumberAndActive(@Param("levelNumber") Integer levelNumber);

    @Query("SELECT ic FROM IcReviewLevelConfig ic WHERE ic.levelNumber = :levelNumber")
    IcReviewLevelConfig findByLevelNumber(@Param("levelNumber") Integer levelNumber);

    @Query("SELECT ic FROM IcReviewLevelConfig ic WHERE ic.levelCode = :levelCode AND ic.isActive = true")
    IcReviewLevelConfig findByLevelCodeAndActive(@Param("levelCode") String levelCode);

    @Query("SELECT ic FROM IcReviewLevelConfig ic WHERE ic.decisionStateValue = :decisionStateValue")
    IcReviewLevelConfig findByDecisionStateValue(@Param("decisionStateValue") Integer decisionStateValue);

    @Query("SELECT COUNT(ic) FROM IcReviewLevelConfig ic WHERE ic.isActive = true")
    Long countActiveLevels();
}

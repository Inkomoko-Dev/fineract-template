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
package org.apache.fineract.portfolio.loanaccount.service;

import java.util.List;
import org.apache.fineract.portfolio.loanaccount.data.IcReviewLevelConfigData;

public interface IcReviewLevelConfigReadPlatformService {

    /**
     * Retrieve all active IC review levels ordered by display order
     */
    List<IcReviewLevelConfigData> retrieveAllActiveLevels();

    /**
     * Retrieve all IC review levels (including inactive)
     */
    List<IcReviewLevelConfigData> retrieveAllLevels();

    /**
     * Retrieve a specific IC review level by ID
     */
    IcReviewLevelConfigData retrieveLevel(Long levelId);

    /**
     * Retrieve IC review level by level number
     */
    IcReviewLevelConfigData retrieveLevelByNumber(Integer levelNumber);

    /**
     * Retrieve IC review level by level code
     */
    IcReviewLevelConfigData retrieveLevelByCode(String levelCode);

    /**
     * Retrieve IC review level by decision state value
     */
    IcReviewLevelConfigData retrieveLevelByDecisionStateValue(Integer decisionStateValue);

    /**
     * Get the count of active IC review levels
     */
    Long countActiveLevels();

    /**
     * Get the next IC review level after the given level number
     */
    IcReviewLevelConfigData retrieveNextLevel(Integer currentLevelNumber);

    /**
     * Get the previous IC review level before the given level number
     */
    IcReviewLevelConfigData retrievePreviousLevel(Integer currentLevelNumber);
}

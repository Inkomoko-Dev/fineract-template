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
package org.apache.fineract.portfolio.loanaccount.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simple DTO for active IC review levels used in the approval matrix template API response.
 * This provides a simplified view of the IC review level configuration for the frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveIcReviewLevelData {

    private Integer levelNumber;
    private String levelName;
    private String levelCode;
    private Integer stateValue;

    /**
     * Creates an ActiveIcReviewLevelData from IcReviewLevelConfigData
     * @param configData the source configuration data
     * @return a new ActiveIcReviewLevelData instance
     */
    public static ActiveIcReviewLevelData fromConfigData(IcReviewLevelConfigData configData) {
        return new ActiveIcReviewLevelData(
                configData.getLevelNumber(),
                configData.getLevelName(),
                configData.getLevelCode(),
                configData.getDecisionStateValue()
        );
    }
}

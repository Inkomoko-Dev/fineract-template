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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data transfer object for dynamic IC review level approval matrix configuration.
 * This represents the approval criteria for a specific IC review level.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class LoanApprovalMatrixLevelData {

    private Long id;
    private Integer levelNumber;
    private Long icReviewLevelId;
    private String icReviewLevelName;

    // Unsecured First Cycle
    private BigDecimal unsecuredFirstCycleMaxAmount;
    private Integer unsecuredFirstCycleMinTerm;
    private Integer unsecuredFirstCycleMaxTerm;

    // Unsecured Second Cycle
    private BigDecimal unsecuredSecondCycleMaxAmount;
    private Integer unsecuredSecondCycleMinTerm;
    private Integer unsecuredSecondCycleMaxTerm;

    // Secured First Cycle
    private BigDecimal securedFirstCycleMaxAmount;
    private Integer securedFirstCycleMinTerm;
    private Integer securedFirstCycleMaxTerm;

    // Secured Second Cycle
    private BigDecimal securedSecondCycleMaxAmount;
    private Integer securedSecondCycleMinTerm;
    private Integer securedSecondCycleMaxTerm;
}

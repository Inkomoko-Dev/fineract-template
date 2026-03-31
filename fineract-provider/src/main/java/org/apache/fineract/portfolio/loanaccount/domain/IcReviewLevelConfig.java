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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;

/**
 * Entity representing IC Review Level Configuration
 * This allows dynamic configuration of IC review levels beyond the hardcoded 5 levels
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "m_ic_review_level_config")
public class IcReviewLevelConfig extends AbstractAuditableCustom {

    @Column(name = "level_number", nullable = false)
    private Integer levelNumber;

    @Column(name = "level_name", length = 100, nullable = false)
    private String levelName;

    @Column(name = "level_code", length = 50, nullable = false, unique = true)
    private String levelCode;

    @Column(name = "decision_state_value", nullable = false, unique = true)
    private Integer decisionStateValue;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    public IcReviewLevelConfig(Integer levelNumber, String levelName, String levelCode,
                                Integer decisionStateValue, Boolean isActive, Integer displayOrder) {
        this.levelNumber = levelNumber;
        this.levelName = levelName;
        this.levelCode = levelCode;
        this.decisionStateValue = decisionStateValue;
        this.isActive = isActive;
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return this.isActive != null && this.isActive;
    }
}

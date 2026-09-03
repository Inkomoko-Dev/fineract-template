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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for validation rules of bulk reschedule operation. Contains constraints and requirements
 * that must be satisfied during bulk reschedule.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRulesDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Whether the current interest rate in filter must be an exact match */
    private Boolean currentInterestRateIsExact;

    /** Whether the new interest rate is manual input (true) or selected from options (false) */
    private Boolean newInterestRateIsManualInput;

    /** Minimum allowed value for new interest rate */
    private BigDecimal newInterestRateMinValue;

    /** Maximum allowed value for new interest rate */
    private BigDecimal newInterestRateMaxValue;

    /** Whether bulk reschedule operations require approval */
    private Boolean requiresApproval;

    /** Roles that can approve bulk reschedule operations */
    private List<String> approvalRoles;
}

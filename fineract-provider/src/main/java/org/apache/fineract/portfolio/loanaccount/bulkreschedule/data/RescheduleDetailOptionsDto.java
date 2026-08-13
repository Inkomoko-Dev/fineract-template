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
import java.util.Collection;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.portfolio.charge.data.ChargeData;

/**
 * DTO for reschedule detail options shown on the bulk reschedule form.
 * Mirrors the options returned by the individual reschedule template endpoint,
 * with the addition of reschedule-from-date strategies.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleDetailOptionsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Available reschedule reason code values */
    private List<CodeValueData> rescheduleReasons;

    /** Available overdue charge handling method code values */
    private List<CodeValueData> overdueChargeHandlingOptions;

    /** Charges available to carry forward (loan applicable penalties) */
    private Collection<ChargeData> availableCarryForwardCharges;

    /** Whether future repayment adjustment is enabled globally */
    private Boolean adjustFuturePayments;

    /** Available strategies for deriving reschedule-from-date per loan */
    private List<EnumOptionData> rescheduleFromDateStrategies;
}


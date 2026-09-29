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
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for a successfully rescheduled loan. Contains details about the reschedule operation including
 * the new rescheduled loan ID and dates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRescheduleSuccessDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** The ID of the original loan that was rescheduled */
    private Long loanId;

    /** The name of the client */
    private String clientName;

    /** The previous interest rate before reschedule */
    private BigDecimal previousInterestRate;

    /** The new interest rate after reschedule */
    private BigDecimal newInterestRate;

    /** The ID of the newly created rescheduled loan */
    private Long rescheduledLoanId;

    /** The date when the next reschedule can be applied */
    private LocalDate nextRescheduleDate;

    /** The note added to the loan record during reschedule */
    private String noteAdded;
}

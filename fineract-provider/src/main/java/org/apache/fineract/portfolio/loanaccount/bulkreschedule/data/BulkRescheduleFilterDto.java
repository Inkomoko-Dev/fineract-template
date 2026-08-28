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
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.RescheduleFromDateStrategy;

/**
 * DTO for capturing bulk reschedule filter criteria. Defines which loans should be included in a
 * rescheduling operation based on office, status, product, officer, and interest rate criteria.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BulkRescheduleFilterDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** ID of the office for which to reschedule loans */
    private Long officeId;

    /** Loan status code (e.g., ACTIVE - "300"). Null includes all statuses. */
    private String loanStatus;

    /** Strategy for deriving rescheduleFromDate per loan: FIRST_INSTALLMENT or NEXT_UNPAID */
    private RescheduleFromDateStrategy rescheduleFromDateStrategy;

    /** Current interest rate to match (exact match, optional) */
    private BigDecimal currentInterestRate;

    /** Optional filter: one or more loan product IDs */
    private List<Long> loanProductIds;

    /** Optional filter: one or more loan officer IDs */
    private List<Long> loanOfficerIds;

    /** Optional filter: list of loan IDs to exclude from rescheduling */
    private List<Long> excludedLoanIds;
}

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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

/**
 * DTO for filter options available for bulk reschedule. Contains all dropdown data needed for
 * filtering loans for bulk reschedule operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterOptionsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Available offices to filter by */
    private List<OfficeOptionDto> offices;

    /** Available loan statuses */
    private List<EnumOptionData> loanStatuses;

    /** Available loan products */
    private List<LoanProductOptionDto> loanProducts;

    /** Available loan officers */
    private List<LoanOfficerOptionDto> loanOfficers;
}

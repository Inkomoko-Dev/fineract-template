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

import java.util.Collection;
import java.util.List;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverData;
import org.apache.fineract.useradministration.data.AppUserData;

public interface HistoricalPenaltyWaiverReadPlatformService {

    HistoricalPenaltyWaiverData retrieveOne(Long waiverId);

    List<HistoricalPenaltyWaiverData> retrieveByLoanId(Long loanId);

    List<HistoricalPenaltyWaiverData> retrievePendingApprovalQueue();

    /**
     * Users who may approve a waiver on this loan: holders of the approve permission at or above the loan's office,
     * narrowed to those mapped as approvers for the loan's product.
     */
    Collection<AppUserData> retrieveApproverOptions(Long loanId);
}

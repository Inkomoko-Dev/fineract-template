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

import java.io.Serializable;

/** Whether a historical penalty waiver needs approving, and which threshold said so. */
public class HistoricalPenaltyWaiverApprovalRequirement implements Serializable {

    public static final String TRIGGER_AMOUNT = "AMOUNT";
    public static final String TRIGGER_AGE = "AGE";
    public static final String TRIGGER_BOTH = "BOTH";

    private final boolean required;
    private final String trigger;

    private HistoricalPenaltyWaiverApprovalRequirement(final boolean required, final String trigger) {
        this.required = required;
        this.trigger = trigger;
    }

    public static HistoricalPenaltyWaiverApprovalRequirement notRequired() {
        return new HistoricalPenaltyWaiverApprovalRequirement(false, null);
    }

    public static HistoricalPenaltyWaiverApprovalRequirement of(final boolean amountBreached, final boolean ageBreached) {
        if (amountBreached && ageBreached) {
            return new HistoricalPenaltyWaiverApprovalRequirement(true, TRIGGER_BOTH);
        }
        if (amountBreached) {
            return new HistoricalPenaltyWaiverApprovalRequirement(true, TRIGGER_AMOUNT);
        }
        if (ageBreached) {
            return new HistoricalPenaltyWaiverApprovalRequirement(true, TRIGGER_AGE);
        }
        return notRequired();
    }

    public boolean isRequired() {
        return this.required;
    }

    public String getTrigger() {
        return this.trigger;
    }
}

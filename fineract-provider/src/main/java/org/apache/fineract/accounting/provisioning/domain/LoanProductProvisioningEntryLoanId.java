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
package org.apache.fineract.accounting.provisioning.domain;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class LoanProductProvisioningEntryLoanId implements Serializable {

    @Column(name = "loanproduct_provision_entry_id")
    private Long loanProductProvisionEntryId;

    @Column(name = "loan_id")
    private Long loanId;

    protected LoanProductProvisioningEntryLoanId() {}

    public LoanProductProvisioningEntryLoanId(Long loanProductProvisionEntryId, Long loanId) {
        this.loanProductProvisionEntryId = loanProductProvisionEntryId;
        this.loanId = loanId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LoanProductProvisioningEntryLoanId)) {
            return false;
        }
        LoanProductProvisioningEntryLoanId other = (LoanProductProvisioningEntryLoanId) obj;
        return Objects.equals(this.loanProductProvisionEntryId, other.loanProductProvisionEntryId)
                && Objects.equals(this.loanId, other.loanId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.loanProductProvisionEntryId, this.loanId);
    }
}

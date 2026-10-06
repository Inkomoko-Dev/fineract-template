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
package org.apache.fineract.portfolio.loanaccount.excessrefund.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoanExcessRefundStatusTest {

    @Test
    void terminalAndOpenClassification() {
        assertThat(LoanExcessRefundStatus.PENDING_APPROVAL.isOpen()).isTrue();
        assertThat(LoanExcessRefundStatus.PAID.isOpen()).isTrue();
        assertThat(LoanExcessRefundStatus.PAYMENT_FAILED.isOpen()).isTrue();
        assertThat(LoanExcessRefundStatus.POSTED.isTerminal()).isTrue();
        assertThat(LoanExcessRefundStatus.REJECTED.isTerminal()).isTrue();
        assertThat(LoanExcessRefundStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(LoanExcessRefundStatus.fromInt(450)).isEqualTo(LoanExcessRefundStatus.PAYMENT_FAILED);
    }
}

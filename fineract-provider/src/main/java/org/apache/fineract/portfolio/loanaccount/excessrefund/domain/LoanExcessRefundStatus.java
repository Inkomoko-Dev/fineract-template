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

import java.util.Arrays;
import java.util.Set;

public enum LoanExcessRefundStatus {

    PENDING_APPROVAL(100), //
    APPROVED(200), //
    PAYMENT_SUBMITTED(300), //
    PAID(400), //
    PAYMENT_FAILED(450), //
    POSTED(500), //
    REJECTED(600), //
    CANCELLED(700);

    private static final Set<LoanExcessRefundStatus> TERMINAL = Set.of(POSTED, REJECTED, CANCELLED);

    private final int value;

    LoanExcessRefundStatus(final int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isOpen() {
        return !isTerminal();
    }

    public static LoanExcessRefundStatus fromInt(final Integer value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(values()).filter(s -> s.value == value).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown excess refund status: " + value));
    }
}

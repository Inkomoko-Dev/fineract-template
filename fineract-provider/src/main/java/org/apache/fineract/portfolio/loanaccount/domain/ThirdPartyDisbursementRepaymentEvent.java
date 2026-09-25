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
package org.apache.fineract.portfolio.loanaccount.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_third_party_disbursement_repayment_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_third_party_repayment_event_transaction_action", columnNames = { "loan_transaction_id", "action" }) })
@Getter
@NoArgsConstructor
public class ThirdPartyDisbursementRepaymentEvent extends AbstractPersistableCustom {

    @Column(name = "loan_transaction_id", nullable = false)
    private Long loanTransactionId;
    @Column(name = "partner_code", nullable = false, length = 50)
    private String partnerCode;
    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;
    @Column(name = "action", nullable = false, length = 30)
    private String action;
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(name = "delivery_status", nullable = false, length = 30)
    private String deliveryStatus;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_on_utc", nullable = false)
    private OffsetDateTime nextAttemptOn;
    @Column(name = "last_error", length = 1000)
    private String lastError;

    public ThirdPartyDisbursementRepaymentEvent(final Long loanTransactionId, final String partnerCode, final String eventId,
            final String action, final String payload) {
        this.loanTransactionId = loanTransactionId;
        this.partnerCode = partnerCode;
        this.eventId = eventId;
        this.action = action;
        this.payload = payload;
        this.deliveryStatus = "PENDING";
        this.nextAttemptOn = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markDelivered() {
        this.deliveryStatus = "DELIVERED";
        this.lastError = null;
    }

    public void markRetry(final String error) {
        this.attemptCount++;
        this.deliveryStatus = this.attemptCount >= 8 ? "FAILED" : "PENDING";
        this.nextAttemptOn = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(Math.min(60, 1L << Math.min(this.attemptCount, 6)));
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
    }
}

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
package org.apache.fineract.useradministration.domain;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;

@Entity
@Table(name = "m_appuser_password_reset")
public class AppUserPasswordReset extends AbstractPersistableCustom {

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "email_hint", length = 100)
    private String emailHint;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    protected AppUserPasswordReset() {}

    public AppUserPasswordReset(final AppUser user, final String tokenHash, final String emailHint, final LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.emailHint = emailHint;
        this.attemptCount = 0;
        this.used = false;
        this.expiresAt = expiresAt;
        this.createdAt = DateUtils.getLocalDateTimeOfSystem();
    }

    public AppUser getUser() {
        return this.user;
    }

    public String getTokenHash() {
        return this.tokenHash;
    }

    public String getEmailHint() {
        return this.emailHint;
    }

    public int getAttemptCount() {
        return this.attemptCount;
    }

    public boolean isUsed() {
        return this.used;
    }

    public LocalDateTime getExpiresAt() {
        return this.expiresAt;
    }

    public boolean isExpired() {
        return DateUtils.getLocalDateTimeOfSystem().isAfter(this.expiresAt);
    }

    public void incrementAttempt() {
        this.attemptCount++;
    }

    public void markUsed() {
        this.used = true;
        this.usedAt = DateUtils.getLocalDateTimeOfSystem();
    }
}

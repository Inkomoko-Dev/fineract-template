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
package org.apache.fineract.portfolio.creditdecision.service;

import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A fuse around the decision engine. After several failed calls in a row it opens and calls are not made for a while, so
 * applications sent during an outage fail at once instead of each waiting out the full time limit and its retries, and the
 * engine is left alone to recover. After the pause one trial call is let through: if it works the fuse closes, if it fails
 * the fuse opens again.
 *
 * Only a call that did not get a proper answer counts as a failure: a timeout, a connection error or a 5xx. An answer of any
 * other kind, a refusal included, shows the engine is up. One breaker is shared by every application, because they all call
 * the same engine. The state is kept in memory, so each Fineract instance has its own.
 */
@Slf4j
@Component
public class CreditDecisionCircuitBreaker {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final int failureThreshold;
    private final long openMillis;
    private final LongSupplier clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openUntil;
    private boolean trialInFlight;

    @org.springframework.beans.factory.annotation.Autowired
    public CreditDecisionCircuitBreaker(@Value("${fineract.creditdecision.breaker.failure-threshold:5}") final int failureThreshold,
            @Value("${fineract.creditdecision.breaker.open-seconds:60}") final int openSeconds) {
        this(failureThreshold, openSeconds * 1000L, System::currentTimeMillis);
    }

    CreditDecisionCircuitBreaker(final int failureThreshold, final long openMillis, final LongSupplier clock) {
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
        this.clock = clock;
    }

    /** A breaker that never opens, for callers that do not want one. */
    public static CreditDecisionCircuitBreaker disabled() {
        return new CreditDecisionCircuitBreaker(0, 0, System::currentTimeMillis);
    }

    /** Whether a call may be made now. A true answer must be followed by {@link #success()} or {@link #failure()}. */
    public synchronized boolean tryAcquire() {
        if (failureThreshold <= 0) {
            return true;
        }
        switch (state) {
            case OPEN:
                if (clock.getAsLong() < openUntil) {
                    return false;
                }
                state = State.HALF_OPEN;
                trialInFlight = true;
                log.info("Decision engine circuit breaker is half open: letting one trial call through");
                return true;
            case HALF_OPEN:
                if (trialInFlight) {
                    return false;
                }
                trialInFlight = true;
                return true;
            default:
                return true;
        }
    }

    /** The engine answered, whatever the answer was. */
    public synchronized void success() {
        if (failureThreshold <= 0) {
            return;
        }
        if (state != State.CLOSED) {
            log.info("Decision engine circuit breaker closed: the engine answered");
        }
        state = State.CLOSED;
        consecutiveFailures = 0;
        trialInFlight = false;
    }

    /** The call timed out, could not connect or got a 5xx. */
    public synchronized void failure() {
        if (failureThreshold <= 0) {
            return;
        }
        trialInFlight = false;
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        consecutiveFailures++;
        if (state == State.CLOSED && consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    public synchronized State state() {
        return state;
    }

    /** Whole seconds until a trial call is allowed, or 0 when the fuse is not open. */
    public synchronized long secondsUntilRetry() {
        if (state != State.OPEN) {
            return 0;
        }
        return Math.max(1, (openUntil - clock.getAsLong() + 999) / 1000);
    }

    private void open() {
        state = State.OPEN;
        openUntil = clock.getAsLong() + openMillis;
        log.warn("Decision engine circuit breaker opened after {} failed calls in a row; no calls for {} seconds", consecutiveFailures,
                openMillis / 1000);
    }
}

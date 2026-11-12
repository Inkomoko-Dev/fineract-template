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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.portfolio.creditdecision.service.CreditDecisionCircuitBreaker.State;
import org.junit.jupiter.api.Test;

class CreditDecisionCircuitBreakerTest {

    private final long[] now = { 10_000L };

    private CreditDecisionCircuitBreaker breaker(final int threshold) {
        return new CreditDecisionCircuitBreaker(threshold, 60_000, () -> now[0]);
    }

    @Test
    void staysClosedBelowTheThresholdAndAnAnswerResetsTheCount() {
        final CreditDecisionCircuitBreaker b = breaker(3);
        for (int i = 0; i < 2; i++) {
            assertTrue(b.tryAcquire());
            b.failure();
        }
        assertTrue(b.tryAcquire());
        b.success();
        for (int i = 0; i < 2; i++) {
            assertTrue(b.tryAcquire());
            b.failure();
        }
        assertEquals(State.CLOSED, b.state());
    }

    @Test
    void opensAfterTheThresholdAndRefusesCallsForThePause() {
        final CreditDecisionCircuitBreaker b = breaker(3);
        for (int i = 0; i < 3; i++) {
            assertTrue(b.tryAcquire());
            b.failure();
        }
        assertEquals(State.OPEN, b.state());
        assertFalse(b.tryAcquire());
        assertEquals(60, b.secondsUntilRetry());
        now[0] += 30_000;
        assertFalse(b.tryAcquire());
        assertEquals(30, b.secondsUntilRetry());
    }

    @Test
    void afterThePauseOnlyOneTrialCallIsAllowedAtATime() {
        final CreditDecisionCircuitBreaker b = breaker(1);
        b.tryAcquire();
        b.failure();
        now[0] += 60_000;

        assertTrue(b.tryAcquire(), "the trial call");
        assertEquals(State.HALF_OPEN, b.state());
        assertFalse(b.tryAcquire(), "nobody else while the trial is out");
    }

    @Test
    void aSuccessfulTrialClosesItAndAFailedOneOpensItAgainForAFullPause() {
        final CreditDecisionCircuitBreaker b = breaker(1);
        b.tryAcquire();
        b.failure();
        now[0] += 60_000;
        assertTrue(b.tryAcquire());
        b.failure();
        assertEquals(State.OPEN, b.state());
        assertFalse(b.tryAcquire());
        assertEquals(60, b.secondsUntilRetry());

        now[0] += 60_000;
        assertTrue(b.tryAcquire());
        b.success();
        assertEquals(State.CLOSED, b.state());
        assertTrue(b.tryAcquire());
        assertTrue(b.tryAcquire());
    }

    @Test
    void aThresholdOfZeroSwitchesItOff() {
        final CreditDecisionCircuitBreaker b = breaker(0);
        for (int i = 0; i < 20; i++) {
            assertTrue(b.tryAcquire());
            b.failure();
        }
        assertEquals(State.CLOSED, b.state());
        assertTrue(CreditDecisionCircuitBreaker.disabled().tryAcquire());
    }
}

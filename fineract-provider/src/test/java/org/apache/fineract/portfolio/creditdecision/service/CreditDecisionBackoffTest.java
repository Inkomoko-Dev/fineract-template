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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CreditDecisionBackoffTest {

    private final CreditDecisionBackoff backoff = new CreditDecisionBackoff(500, 4000);

    @Test
    void theWaitDoublesWithEachFailedAttemptUpToTheCeiling() {
        // random 0.5 means no spread either way
        assertEquals(500, backoff.delayMillis(1, 0.5));
        assertEquals(1000, backoff.delayMillis(2, 0.5));
        assertEquals(2000, backoff.delayMillis(3, 0.5));
        assertEquals(4000, backoff.delayMillis(4, 0.5));
        assertEquals(4000, backoff.delayMillis(5, 0.5));
        assertEquals(4000, backoff.delayMillis(60, 0.5));
    }

    @Test
    void theSpreadIsTwentyPercentEitherWayAndNeverPassesTheCeiling() {
        assertEquals(400, backoff.delayMillis(1, 0.0));
        assertEquals(600, backoff.delayMillis(1, 1.0));
        assertEquals(1600, backoff.delayMillis(3, 0.0));
        assertTrue(backoff.delayMillis(10, 1.0) <= 4000);
    }

    @Test
    void noBaseMeansNoWaiting() {
        assertEquals(0, CreditDecisionBackoff.none().delayMillis(3, 0.9));
        assertTrue(CreditDecisionBackoff.none().pause(1));
    }
}

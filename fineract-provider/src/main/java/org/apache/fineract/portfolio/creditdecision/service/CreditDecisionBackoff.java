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

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How long to wait before asking the decision engine again. The wait doubles with each failed attempt, up to a ceiling, and
 * is spread by a random amount either way so that applications that failed together do not all come back together.
 */
@Component
public class CreditDecisionBackoff {

    /** The random spread, as a share of the wait: 0.2 is 20% either way. */
    static final double JITTER = 0.2;

    private final long baseMillis;
    private final long maxMillis;

    @Autowired
    public CreditDecisionBackoff(@Value("${fineract.creditdecision.backoff.base-millis:500}") final long baseMillis,
            @Value("${fineract.creditdecision.backoff.max-millis:4000}") final long maxMillis) {
        this.baseMillis = Math.max(0, baseMillis);
        this.maxMillis = Math.max(this.baseMillis, maxMillis);
    }

    /** No waiting at all, for tests. */
    public static CreditDecisionBackoff none() {
        return new CreditDecisionBackoff(0, 0);
    }

    /** The wait after the given failed attempt (1 for the first), with {@code random} in [0, 1) deciding the spread. */
    long delayMillis(final int attempt, final double random) {
        if (baseMillis == 0) {
            return 0;
        }
        final int doublings = Math.min(Math.max(0, attempt - 1), 20);
        final long plain = Math.min(maxMillis, baseMillis << doublings);
        final double spread = 1 - JITTER + 2 * JITTER * random;
        return Math.min(maxMillis, Math.round(plain * spread));
    }

    /** Waits. Returns false when the thread was interrupted. */
    public boolean pause(final int attempt) {
        final long wait = delayMillis(attempt, ThreadLocalRandom.current().nextDouble());
        if (wait <= 0) {
            return true;
        }
        try {
            Thread.sleep(wait);
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}

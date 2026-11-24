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

import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionLoanData;
import org.junit.jupiter.api.Test;

class CreditDecisionReasonCodesTest {

    @Test
    void mapsStuckAndEngineFailures() {
        assertEquals(CreditDecisionReasonCodes.SEND_STUCK,
                CreditDecisionReasonCodes.forException(CreditDecisionLoanData.EXCEPTION_STUCK, "anything"));
        assertEquals(CreditDecisionReasonCodes.ENGINE_TIMEOUT,
                CreditDecisionReasonCodes.forException(CreditDecisionLoanData.EXCEPTION_FAILED, "Connection timed out"));
        assertEquals(CreditDecisionReasonCodes.ENGINE_UNAVAILABLE,
                CreditDecisionReasonCodes.forException(CreditDecisionLoanData.EXCEPTION_FAILED, "The decision engine could not be reached"));
        assertEquals(CreditDecisionReasonCodes.ENGINE_UNAVAILABLE,
                CreditDecisionReasonCodes.forException(CreditDecisionLoanData.EXCEPTION_FAILED, "Connection refused"));
        assertEquals(CreditDecisionReasonCodes.PAYLOAD_VALIDATION,
                CreditDecisionReasonCodes.forException(CreditDecisionLoanData.EXCEPTION_FAILED,
                        "The decision engine refused the request with HTTP 400"));
        assertEquals(CreditDecisionReasonCodes.BUREAU_MISSING,
                CreditDecisionReasonCodes.forException(CreditDecisionLoanData.EXCEPTION_HELD, "Bureau report missing"));
        assertTrue(CreditDecisionReasonCodes.contains("A,KILL_SWITCH,B", CreditDecisionReasonCodes.KILL_SWITCH));
        assertTrue(CreditDecisionReasonCodes.contains(CreditDecisionReasonCodes.EXCEPTION_OVERRIDE_MANUAL_IC,
                CreditDecisionReasonCodes.EXCEPTION_OVERRIDE_MANUAL_IC));
    }

    @Test
    void hashesAreStableAndMatch() {
        final String body = "{\"requestId\":\"req_abc\"}";
        final String hash = CreditDecisionHash.sha256(body);
        assertEquals(64, hash.length());
        assertTrue(CreditDecisionHash.matches(body, hash));
        assertEquals("CHANGED", CreditDecisionLogReader.integrity(body, hash, "{}", hash));
        assertEquals("INTACT", CreditDecisionLogReader.integrity(body, hash, body, CreditDecisionHash.sha256(body)));
    }
}

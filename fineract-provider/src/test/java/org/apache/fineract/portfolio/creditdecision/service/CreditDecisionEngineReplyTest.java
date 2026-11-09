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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CreditDecisionEngineReplyTest {

    @Test
    void readsDecisionScoreAndReasonCodes() {
        final CreditDecisionEngineReply reply = CreditDecisionEngineReply
                .parse("{\"decision\":\"review\",\"score\":612.5,\"reasonCodes\":[\"R12\",\"R31\"]}");
        assertTrue(reply.usable());
        assertEquals("REVIEW", reply.getDecision());
        assertEquals(new BigDecimal("612.5"), reply.getScore());
        assertEquals("R12, R31", reply.getReasonCodes());
    }

    @Test
    void acceptsOtherNamesForTheSameThings() {
        final CreditDecisionEngineReply reply = CreditDecisionEngineReply.parse("{\"outcome\":\"no_go\",\"score\":\"431\",\"reasons\":\"BUREAU_LISTED\"}");
        assertEquals("NO-GO", reply.getDecision());
        assertEquals(new BigDecimal("431"), reply.getScore());
        assertEquals("BUREAU_LISTED", reply.getReasonCodes());
        assertEquals("NO-GO", CreditDecisionEngineReply.parse("{\"decision\":\"NOGO\"}").getDecision());
    }

    @Test
    void scoreAndReasonsAreOptional() {
        final CreditDecisionEngineReply reply = CreditDecisionEngineReply.parse("{\"decision\":\"GO\"}");
        assertTrue(reply.usable());
        assertNull(reply.getScore());
        assertNull(reply.getReasonCodes());
    }

    @Test
    void anythingElseIsNotUsableAndSaysWhy() {
        assertFalse(CreditDecisionEngineReply.parse("not json").usable());
        assertFalse(CreditDecisionEngineReply.parse("[]").usable());
        assertFalse(CreditDecisionEngineReply.parse("").usable());
        assertEquals("The engine reply has no decision", CreditDecisionEngineReply.parse("{\"score\":5}").getProblem());
        final CreditDecisionEngineReply odd = CreditDecisionEngineReply.parse("{\"decision\":\"MAYBE\"}");
        assertFalse(odd.usable());
        assertTrue(odd.getProblem().contains("MAYBE"));
    }
}

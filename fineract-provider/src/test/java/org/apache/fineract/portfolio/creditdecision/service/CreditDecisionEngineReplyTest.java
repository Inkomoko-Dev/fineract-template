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
    void readsTheProcessFlowReply() {
        final CreditDecisionEngineReply reply = CreditDecisionEngineReply.parse("{\"scoringStatus\":\"COMPLETED\","
                + "\"recommendation\":\"IC_REVIEW_REQUIRED\",\"score\":612.5,\"riskTier\":\"MEDIUM\","
                + "\"maxAllowedExposure\":250000,\"modelVersion\":\"scorecard-v3\",\"reasonCodes\":[\"R12\",\"R31\"]}");
        assertTrue(reply.usable());
        assertEquals("IC_REVIEW_REQUIRED", reply.getDecision());
        assertEquals(new BigDecimal("612.5"), reply.getScore());
        assertEquals("MEDIUM", reply.getRiskTier());
        assertEquals(new BigDecimal("250000"), reply.getMaxExposure());
        assertEquals("scorecard-v3", reply.getModelVersion());
        assertEquals("R12, R31", reply.getReasonCodes());
    }

    @Test
    void stillReadsTheFirstDraftNames() {
        final CreditDecisionEngineReply reply = CreditDecisionEngineReply
                .parse("{\"outcome\":\"no_go\",\"score\":\"431\",\"reasons\":\"BUREAU_LISTED\",\"engine\":\"rules-1\"}");
        assertEquals("INELIGIBLE", reply.getDecision());
        assertEquals(new BigDecimal("431"), reply.getScore());
        assertEquals("BUREAU_LISTED", reply.getReasonCodes());
        assertEquals("rules-1", reply.getModelVersion());
        assertEquals("INELIGIBLE", CreditDecisionEngineReply.parse("{\"decision\":\"NOGO\"}").getDecision());
        assertEquals("AUTO_APPROVE", CreditDecisionEngineReply.parse("{\"decision\":\"go\"}").getDecision());
        assertEquals("IC_REVIEW_REQUIRED", CreditDecisionEngineReply.parse("{\"decision\":\"Review\"}").getDecision());
    }

    @Test
    void anIneligibleReplyMayOnlyListTheFailedRules() {
        final CreditDecisionEngineReply reply = CreditDecisionEngineReply.parse("{\"recommendation\":\"INELIGIBLE\","
                + "\"eligibilityResults\":{\"failedRules\":[\"AGE_BELOW_MINIMUM\",\"ACTIVE_DEFAULT\"]}}");
        assertEquals("INELIGIBLE", reply.getDecision());
        assertEquals("AGE_BELOW_MINIMUM, ACTIVE_DEFAULT", reply.getReasonCodes());
    }

    @Test
    void scoreTierAndReasonsAreOptional() {
        final CreditDecisionEngineReply reply = CreditDecisionEngineReply.parse("{\"recommendation\":\"AUTO_APPROVE\"}");
        assertTrue(reply.usable());
        assertNull(reply.getScore());
        assertNull(reply.getReasonCodes());
        assertNull(reply.getRiskTier());
        assertNull(reply.getMaxExposure());
        assertNull(reply.getModelVersion());
    }

    @Test
    void aScoringThatDidNotCompleteIsNotUsable() {
        final CreditDecisionEngineReply reply = CreditDecisionEngineReply
                .parse("{\"scoringStatus\":\"FAILED\",\"recommendation\":\"AUTO_APPROVE\"}");
        assertFalse(reply.usable());
        assertTrue(reply.getProblem().contains("FAILED"));
    }

    @Test
    void anythingElseIsNotUsableAndSaysWhy() {
        assertFalse(CreditDecisionEngineReply.parse("not json").usable());
        assertFalse(CreditDecisionEngineReply.parse("[]").usable());
        assertFalse(CreditDecisionEngineReply.parse("").usable());
        assertEquals("The engine reply has no recommendation", CreditDecisionEngineReply.parse("{\"score\":5}").getProblem());
        final CreditDecisionEngineReply odd = CreditDecisionEngineReply.parse("{\"recommendation\":\"MAYBE\"}");
        assertFalse(odd.usable());
        assertTrue(odd.getProblem().contains("MAYBE"));
    }
}

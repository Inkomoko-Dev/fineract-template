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
package org.apache.fineract.commands.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.util.Locale;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * CGLT-690: pins senior-checker amount gate boundaries (strict above) and disabled-config = gate off.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PartialWriteOffMakerCheckerPolicyTest {

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Mock
    private FromJsonHelper fromApiJsonHelper;

    private PartialWriteOffMakerCheckerPolicy policy;

    @BeforeEach
    public void setUp() {
        when(this.configurationDomainService.retrievePartialWriteOffApprovalAmountThreshold()).thenReturn(50000L);
        this.policy = new PartialWriteOffMakerCheckerPolicy(this.configurationDomainService);
    }

    @Test
    public void justUnderTheAmountThresholdNeedsNoSeniorChecker() {
        assertFalse(this.policy.requiresSeniorChecker(new BigDecimal("49999.99")));
    }

    @Test
    public void exactlyTheAmountThresholdNeedsNoSeniorChecker() {
        assertFalse(this.policy.requiresSeniorChecker(new BigDecimal("50000")), "gate is above the threshold, not at it");
    }

    @Test
    public void justOverTheAmountThresholdNeedsSeniorChecker() {
        assertTrue(this.policy.requiresSeniorChecker(new BigDecimal("50000.01")));
    }

    @Test
    public void aDisabledAmountRowSwitchesTheSeniorGateOff() {
        when(this.configurationDomainService.retrievePartialWriteOffApprovalAmountThreshold()).thenReturn(null);
        assertFalse(this.policy.requiresSeniorChecker(new BigDecimal("10000000")),
                "a disabled row means the senior gate is off, not a threshold of zero");
    }

    @Test
    public void nullAmountDoesNotRequireSeniorChecker() {
        assertFalse(this.policy.requiresSeniorChecker(null));
    }

    @Test
    public void extractWriteOffAmountSumsComponentPortions() {
        final String json = "{\"locale\":\"en\",\"principalPortion\":100.00,\"interestPortion\":50.50,"
                + "\"feeChargesPortion\":10,\"penaltyChargesPortion\":5.25}";
        when(this.fromApiJsonHelper.parse(json)).thenReturn(JsonParser.parseString(json));
        when(this.fromApiJsonHelper.extractLocaleParameter(any())).thenReturn(Locale.ENGLISH);
        when(this.fromApiJsonHelper.extractBigDecimalNamed(eq("principalPortion"), any(), eq(Locale.ENGLISH)))
                .thenReturn(new BigDecimal("100.00"));
        when(this.fromApiJsonHelper.extractBigDecimalNamed(eq("interestPortion"), any(), eq(Locale.ENGLISH)))
                .thenReturn(new BigDecimal("50.50"));
        when(this.fromApiJsonHelper.extractBigDecimalNamed(eq("feeChargesPortion"), any(), eq(Locale.ENGLISH)))
                .thenReturn(new BigDecimal("10"));
        when(this.fromApiJsonHelper.extractBigDecimalNamed(eq("penaltyChargesPortion"), any(), eq(Locale.ENGLISH)))
                .thenReturn(new BigDecimal("5.25"));

        assertEquals(0, new BigDecimal("165.75").compareTo(this.policy.extractWriteOffAmount(json, this.fromApiJsonHelper)));
    }

    @Test
    public void isPartialWriteOffCommandMatchesActionName() {
        final CommandSource partial = mock(CommandSource.class);
        when(partial.getActionName()).thenReturn("PARTIALWRITEOFF");
        assertTrue(this.policy.isPartialWriteOffCommand(partial));

        final CommandSource other = mock(CommandSource.class);
        when(other.getActionName()).thenReturn("WRITEOFF");
        assertFalse(this.policy.isPartialWriteOffCommand(other));
    }
}

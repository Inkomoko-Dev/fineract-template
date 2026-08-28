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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.portfolio.loanaccount.data.HistoricalPenaltyWaiverApprovalRequirement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Pins the two approval triggers and, just as importantly, that a disabled configuration row switches its trigger off
 * rather than behaving as a threshold of zero.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HistoricalPenaltyWaiverApprovalPolicyTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 4);

    @Mock
    private ConfigurationDomainService configurationDomainService;

    private HistoricalPenaltyWaiverApprovalPolicy policy;

    @BeforeEach
    public void setUp() {
        when(this.configurationDomainService.retrieveHistoricalPenaltyWaiverApprovalAmountThreshold()).thenReturn(50000L);
        when(this.configurationDomainService.retrieveHistoricalPenaltyWaiverApprovalAgeDays()).thenReturn(90L);
        this.policy = new HistoricalPenaltyWaiverApprovalPolicy(this.configurationDomainService);
    }

    private HistoricalPenaltyWaiverApprovalRequirement determine(final String amount, final LocalDate chargeDueDate) {
        return this.policy.determine(new BigDecimal(amount), chargeDueDate, BUSINESS_DATE);
    }

    private LocalDate dueDaysAgo(final int days) {
        return BUSINESS_DATE.minusDays(days);
    }

    @Test
    public void justUnderTheAmountThresholdNeedsNoApproval() {
        final HistoricalPenaltyWaiverApprovalRequirement requirement = determine("49999.99", dueDaysAgo(10));
        assertFalse(requirement.isRequired());
        assertNull(requirement.getTrigger());
    }

    @Test
    public void exactlyTheAmountThresholdNeedsNoApproval() {
        assertFalse(determine("50000", dueDaysAgo(10)).isRequired(), "the trigger is above the threshold, not at it");
    }

    @Test
    public void justOverTheAmountThresholdNeedsApproval() {
        final HistoricalPenaltyWaiverApprovalRequirement requirement = determine("50000.01", dueDaysAgo(10));
        assertTrue(requirement.isRequired());
        assertEquals(HistoricalPenaltyWaiverApprovalRequirement.TRIGGER_AMOUNT, requirement.getTrigger());
    }

    @Test
    public void justUnderTheAgeThresholdNeedsNoApproval() {
        assertFalse(determine("100", dueDaysAgo(89)).isRequired());
    }

    @Test
    public void exactlyTheAgeThresholdNeedsNoApproval() {
        assertFalse(determine("100", dueDaysAgo(90)).isRequired(), "the trigger is older than the threshold, not at it");
    }

    @Test
    public void justOverTheAgeThresholdNeedsApproval() {
        final HistoricalPenaltyWaiverApprovalRequirement requirement = determine("100", dueDaysAgo(91));
        assertTrue(requirement.isRequired());
        assertEquals(HistoricalPenaltyWaiverApprovalRequirement.TRIGGER_AGE, requirement.getTrigger());
    }

    @Test
    public void bothTriggersBreachedIsReportedAsBoth() {
        final HistoricalPenaltyWaiverApprovalRequirement requirement = determine("60000", dueDaysAgo(120));
        assertTrue(requirement.isRequired());
        assertEquals(HistoricalPenaltyWaiverApprovalRequirement.TRIGGER_BOTH, requirement.getTrigger());
    }

    @Test
    public void aDisabledAmountRowSwitchesTheAmountTriggerOff() {
        when(this.configurationDomainService.retrieveHistoricalPenaltyWaiverApprovalAmountThreshold()).thenReturn(null);

        final HistoricalPenaltyWaiverApprovalRequirement requirement = determine("10000000", dueDaysAgo(10));
        assertFalse(requirement.isRequired(), "a disabled row means the trigger is off, not a threshold of zero");
    }

    @Test
    public void aDisabledAgeRowSwitchesTheAgeTriggerOff() {
        when(this.configurationDomainService.retrieveHistoricalPenaltyWaiverApprovalAgeDays()).thenReturn(null);

        assertFalse(determine("100", dueDaysAgo(3650)).isRequired());
    }

    @Test
    public void bothRowsDisabledNeverRequiresApproval() {
        when(this.configurationDomainService.retrieveHistoricalPenaltyWaiverApprovalAmountThreshold()).thenReturn(null);
        when(this.configurationDomainService.retrieveHistoricalPenaltyWaiverApprovalAgeDays()).thenReturn(null);

        assertFalse(determine("10000000", dueDaysAgo(3650)).isRequired());
    }

    @Test
    public void aDisabledAmountRowLeavesTheAgeTriggerWorking() {
        when(this.configurationDomainService.retrieveHistoricalPenaltyWaiverApprovalAmountThreshold()).thenReturn(null);

        final HistoricalPenaltyWaiverApprovalRequirement requirement = determine("10000000", dueDaysAgo(120));
        assertTrue(requirement.isRequired());
        assertEquals(HistoricalPenaltyWaiverApprovalRequirement.TRIGGER_AGE, requirement.getTrigger());
    }

    @Test
    public void aChargeWithNoDueDateCannotBreachTheAgeTrigger() {
        assertFalse(determine("100", null).isRequired());
    }
}

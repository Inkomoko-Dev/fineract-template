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

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.jobs.annotation.CronTarget;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.portfolio.loanaccount.domain.HistoricalPenaltyWaiverStatus;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiver;
import org.apache.fineract.portfolio.loanaccount.domain.LoanHistoricalPenaltyWaiverRepository;
import org.apache.fineract.useradministration.data.AppUserData;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.service.AppUserReadPlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escalates historical penalty waivers that have sat unapproved past the configured window.
 *
 * <p>
 * Fineract has no reporting line: {@code m_staff} carries no supervisor and {@code AppUser} links only to an office and
 * a staff record. The nearest achievable escalation is therefore another permitted approver in a <em>different</em>
 * office to the one already asked, which in a hierarchy means someone further up. A true manager chain would be a
 * separate piece of work.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalPenaltyWaiverEscalationService {

    private final LoanHistoricalPenaltyWaiverRepository waiverRepository;
    private final AppUserReadPlatformService appUserReadPlatformService;
    private final AppUserRepository appUserRepository;
    private final ConfigurationDomainService configurationDomainService;
    private final HistoricalPenaltyWaiverNotificationService notificationService;

    @Transactional
    @CronTarget(jobName = JobName.ESCALATE_PENDING_HISTORICAL_CORRECTIONS)
    public void escalatePendingHistoricalCorrections() {

        final Long escalationHours = this.configurationDomainService.retrieveHistoricalPenaltyWaiverEscalationHours();
        if (escalationHours == null) {
            return;
        }

        final OffsetDateTime cutoff = DateUtils.getOffsetDateTimeOfTenant().minusHours(escalationHours);
        final List<LoanHistoricalPenaltyWaiver> waiting = this.waiverRepository
                .findNotYetEscalated(HistoricalPenaltyWaiverStatus.PENDING_APPROVAL);

        for (final LoanHistoricalPenaltyWaiver waiver : waiting) {
            if (waiver.getSubmittedOnDate() == null || waiver.getSubmittedOnDate().isAfter(cutoff)) {
                continue;
            }
            final AppUser escalateTo = findEscalationTarget(waiver);
            if (escalateTo == null) {
                log.warn("Historical penalty waiver {} is overdue for approval but no alternative approver is mapped to product {}.",
                        waiver.getId(), waiver.getProductId());
                continue;
            }
            waiver.markEscalated(escalateTo.getId(), DateUtils.getOffsetDateTimeOfTenant());
            this.waiverRepository.save(waiver);
            this.notificationService.notifyEscalated(waiver, escalateTo);
        }
    }

    private AppUser findEscalationTarget(final LoanHistoricalPenaltyWaiver waiver) {

        final Collection<AppUserData> permitted = this.appUserReadPlatformService.retrieveUsersByOfficeAndPermission(waiver.getOfficeId(),
                HistoricalPenaltyWaiverReadPlatformServiceImpl.APPROVE_PERMISSION);

        AppUser sameOfficeFallback = null;
        for (final AppUserData option : permitted) {
            if (waiver.getNextApproverId() != null && option.hasIdentifyOf(waiver.getNextApproverId())) {
                continue;
            }
            final AppUser candidate = this.appUserRepository.findAppUserByName(option.username());
            if (candidate == null || candidate.getOffice() == null) {
                continue;
            }
            if (!candidate.getOffice().getId().equals(waiver.getOfficeId())) {
                return candidate;
            }
            sameOfficeFallback = candidate;
        }
        return sameOfficeFallback;
    }
}

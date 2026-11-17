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

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.creditdecision.data.CreditDecisionData.ProfileData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How a policy version moves from draft to live: the maker submits it, which files a task in the Checker Inbox, and a
 * checker approves or rejects that task, from the profile page or from the inbox. Activation happens only when the
 * checker approves.
 */
@Service
@RequiredArgsConstructor
public class CreditDecisionPolicyWorkflow {

    private final CreditDecisionService service;
    private final CreditDecisionMakerChecker makerChecker;

    /** Locks the version for approval and files the task for a checker. */
    @Transactional
    public ProfileData submit(final Long profileId, final int versionNo, final String reason) {
        final ProfileData profile = service.submit(profileId, versionNo, reason);
        final Long taskId = makerChecker.file(profileId, versionNo, profile.getName(), reason);
        service.linkMakerChecker(profileId, versionNo, taskId);
        return service.profile(profileId);
    }

    /** The maker takes the version back from the checker. */
    @Transactional
    public ProfileData withdraw(final Long profileId, final int versionNo) {
        final Long taskId = service.makerCheckerIdOf(profileId, versionNo);
        final ProfileData profile = service.withdraw(profileId, versionNo);
        if (taskId != null) {
            makerChecker.cancel(taskId);
        }
        return service.profile(profileId);
    }

    /** The checker approves the task of the version. Same as approving it in the Checker Inbox. */
    @Transactional
    public ProfileData approve(final Long profileId, final int versionNo) {
        final Long taskId = service.makerCheckerIdOf(profileId, versionNo);
        if (taskId == null) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.task.missing",
                    "This version has no approval task in the Checker Inbox. Withdraw it and submit it again.");
        }
        makerChecker.approve(taskId);
        return service.profile(profileId);
    }

    /** The checker turns the version down with the reason. Same as rejecting the task in the Checker Inbox. */
    @Transactional
    public ProfileData reject(final Long profileId, final int versionNo, final String reason) {
        final Long taskId = service.makerCheckerIdOf(profileId, versionNo);
        final ProfileData profile = service.reject(profileId, versionNo, reason);
        if (taskId != null) {
            makerChecker.reject(taskId, reason);
        }
        return service.profile(profileId);
    }

    /** Settles versions whose task was closed in the Checker Inbox, then reads the profile. */
    @Transactional
    public ProfileData profile(final Long profileId) {
        service.settleClosedTasks(profileId);
        return service.profile(profileId);
    }

    @Transactional
    public java.util.List<ProfileData> profiles() {
        for (final ProfileData profile : service.profiles()) {
            service.settleClosedTasks(profile.getId());
        }
        return service.profiles();
    }
}

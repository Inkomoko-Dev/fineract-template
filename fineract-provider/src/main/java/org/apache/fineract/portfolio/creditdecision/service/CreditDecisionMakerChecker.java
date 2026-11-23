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

import com.google.gson.JsonObject;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandProcessingService;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts the activation of a policy version through the Maker-Checker workflow of the platform. The task is always filed,
 * whatever the global Maker-Checker setting is: a policy change cannot take effect without a checker. It shows in the
 * Checker Inbox for anyone with the ACTIVATE_CREDITDECISION_CHECKER permission.
 */
@Service
@RequiredArgsConstructor
public class CreditDecisionMakerChecker {

    private final PlatformSecurityContext context;
    private final CommandProcessingService commandProcessingService;
    private final PortfolioCommandSourceWritePlatformService commandSourceService;
    private final CommandSourceRepository commandSourceRepository;
    private final FromJsonHelper fromJsonHelper;

    /** Files the task that activates the version, in the name of the signed-in user (the maker). Returns the task id. */
    @Transactional
    public Long file(final Long profileId, final int versionNo, final String profileName, final String reason) {
        final JsonObject body = new JsonObject();
        body.addProperty("profileName", profileName);
        body.addProperty("versionNo", versionNo);
        body.addProperty("locale", "en");
        body.addProperty("reason", reason == null ? "" : reason.trim());
        final String json = body.toString();
        final CommandWrapper wrapper = new CommandWrapperBuilder().activateCreditDecisionVersion(profileId).withJson(json).build();
        final JsonCommand command = JsonCommand.from(json, fromJsonHelper.parse(json), fromJsonHelper, "CREDITDECISION", profileId, null,
                null, null, null, null, null, wrapper.getHref(), null, null, null);
        final CommandSource task = CommandSource.fullEntryFrom(wrapper, command, context.authenticatedUser());
        return commandProcessingService.logCommand(task).commandId();
    }

    /** The checker approves the task: the version becomes the live one. Needs the checker permission. */
    @Transactional
    public void approve(final Long taskId) {
        commandSourceService.approveEntry(taskId, null);
    }

    /** The checker turns the task down, with the reason. Needs the checker permission. */
    @Transactional
    public void reject(final Long taskId, final String reason) {
        final JsonObject note = new JsonObject();
        note.addProperty("note", reason == null ? "" : reason.trim());
        commandSourceService.rejectEntry(taskId, note.toString());
    }

    /** The maker takes the task back. */
    @Transactional
    public void cancel(final Long taskId) {
        final CommandSource task = commandSourceRepository.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }
        if (!task.isMarkedAsAwaitingApproval()) {
            throw new GeneralPlatformDomainRuleException("error.msg.creditdecision.task.closed",
                    "The approval task of this version has already been decided in the Checker Inbox.");
        }
        final AppUser maker = context.authenticatedUser();
        task.markAsRejected(maker, ZonedDateTime.now(DateUtils.getDateTimeZoneOfTenant()));
        commandSourceRepository.save(task);
    }
}

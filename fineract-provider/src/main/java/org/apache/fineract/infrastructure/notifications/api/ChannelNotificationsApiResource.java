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
package org.apache.fineract.infrastructure.notifications.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.notifications.constants.ChannelNotificationConstants;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.apache.fineract.infrastructure.notifications.data.NotificationResult;
import org.apache.fineract.infrastructure.notifications.serialization.ChannelNotificationValidator;
import org.apache.fineract.infrastructure.notifications.service.NotificationCommandService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Outbound channel notifications facade (WhatsApp today; Novu-ready abstraction).
 * In-app user notifications remain under {@code GET/PUT /notifications}.
 */
@Path("/channelnotifications")
@Component
@Scope("singleton")
@Tag(name = "Channel Notifications", description = "Outbound multi-channel notification dispatch")
@RequiredArgsConstructor
public class ChannelNotificationsApiResource {

    private final PlatformSecurityContext context;
    private final ChannelNotificationValidator channelNotificationValidator;
    private final NotificationCommandService notificationCommandService;
    private final DefaultToApiJsonSerializer<CommandProcessingResult> commandProcessingResultSerializer;

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    public String sendNotification(final String apiRequestBodyAsJson) {
        this.context.authenticatedUser().validateHasCreatePermission(ChannelNotificationConstants.RESOURCE_NAME);
        final NotificationCommand command = channelNotificationValidator.parseCommand(apiRequestBodyAsJson);
        final NotificationResult result = notificationCommandService.send(command);
        if (!result.isAccepted()) {
            throw new GeneralPlatformDomainRuleException("error.msg.channel.notification.rejected", result.getRejectionReason());
        }
        return commandProcessingResultSerializer.serializeResult(CommandProcessingResult.resourceResult(result.getResourceId(), null));
    }
}

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
package org.apache.fineract.infrastructure.notifications.policy;

import org.apache.fineract.infrastructure.notifications.constants.NotificationChannel;
import org.apache.fineract.infrastructure.notifications.constants.NotificationPurpose;
import org.apache.fineract.infrastructure.notifications.data.NotificationCommand;
import org.springframework.stereotype.Component;

/**
 * Default policy until CGLT-677 consent/opt-out records are implemented.
 */
@Component
public class DefaultNotificationPolicy implements NotificationPolicy {

    @Override
    public boolean isAllowed(final NotificationCommand command) {
        return isAllowed(command.getChannel(), command.getPhoneNumber(), command.getPurpose());
    }

    @Override
    public boolean isAllowed(final NotificationChannel channel, final String phoneNumber, final NotificationPurpose purpose) {
        return true;
    }
}

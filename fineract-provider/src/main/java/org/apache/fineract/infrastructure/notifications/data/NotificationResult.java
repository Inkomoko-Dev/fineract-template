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
package org.apache.fineract.infrastructure.notifications.data;

public final class NotificationResult {

    private final Long resourceId;
    private final boolean accepted;
    private final String rejectionReason;

    private NotificationResult(final Long resourceId, final boolean accepted, final String rejectionReason) {
        this.resourceId = resourceId;
        this.accepted = accepted;
        this.rejectionReason = rejectionReason;
    }

    public static NotificationResult accepted(final Long resourceId) {
        return new NotificationResult(resourceId, true, null);
    }

    public static NotificationResult rejected(final String rejectionReason) {
        return new NotificationResult(null, false, rejectionReason);
    }

    public Long getResourceId() {
        return resourceId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}

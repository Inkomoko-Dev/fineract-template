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
package org.apache.fineract.infrastructure.africastalking.data;

import java.util.List;
import lombok.Getter;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;

@Getter
public class ResolvedRecipientData {

    private final String normalizedPhoneNumber;
    private final RecipientType recipientType;
    private final Long clientId;
    private final Long staffId;
    private final List<Long> clientIds;

    private ResolvedRecipientData(final String normalizedPhoneNumber, final RecipientType recipientType, final Long clientId,
            final Long staffId, final List<Long> clientIds) {
        this.normalizedPhoneNumber = normalizedPhoneNumber;
        this.recipientType = recipientType;
        this.clientId = clientId;
        this.staffId = staffId;
        this.clientIds = clientIds == null ? List.of() : List.copyOf(clientIds);
    }

    public static ResolvedRecipientData client(final String normalizedPhoneNumber, final Long clientId) {
        return new ResolvedRecipientData(normalizedPhoneNumber, RecipientType.CLIENT, clientId, null, List.of(clientId));
    }

    public static ResolvedRecipientData staff(final String normalizedPhoneNumber, final Long staffId) {
        return new ResolvedRecipientData(normalizedPhoneNumber, RecipientType.STAFF, null, staffId, List.of());
    }

    public static ResolvedRecipientData unknown(final String normalizedPhoneNumber) {
        return new ResolvedRecipientData(normalizedPhoneNumber, RecipientType.UNKNOWN, null, null, List.of());
    }

    /** Multiple clients share this number — do not auto-bind; require account / picker. */
    public static ResolvedRecipientData multiClient(final String normalizedPhoneNumber, final List<Long> clientIds) {
        return new ResolvedRecipientData(normalizedPhoneNumber, RecipientType.MULTI_CLIENT, null, null, clientIds);
    }

    /** Number matches both a client and a staff member — ask which service before binding. */
    public static ResolvedRecipientData clientAndStaff(final String normalizedPhoneNumber, final Long clientId, final Long staffId) {
        return new ResolvedRecipientData(normalizedPhoneNumber, RecipientType.CLIENT_AND_STAFF, clientId, staffId, List.of(clientId));
    }

    public boolean requiresRoleSelection() {
        return recipientType == RecipientType.CLIENT_AND_STAFF;
    }

    public boolean isAmbiguousClients() {
        return recipientType == RecipientType.MULTI_CLIENT;
    }
}

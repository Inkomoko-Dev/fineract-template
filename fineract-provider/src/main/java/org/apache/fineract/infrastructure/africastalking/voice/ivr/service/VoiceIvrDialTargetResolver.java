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
package org.apache.fineract.infrastructure.africastalking.voice.ivr.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.config.AfricasTalkingProperties;
import org.apache.fineract.infrastructure.africastalking.service.PhoneNumberNormalizer;
import org.apache.fineract.infrastructure.africastalking.voice.ivr.domain.VoiceIvrSession;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VoiceIvrDialTargetResolver {

    private final AfricasTalkingProperties properties;
    private final PhoneNumberNormalizer phoneNumberNormalizer;

    public String resolve(final String actionTarget) {
        return resolve(actionTarget, null);
    }

    /**
     * Prefer the client's assigned investment officer, then the configured department queue number.
     * Returns a comma-separated list for sequential Dial when both are present.
     */
    public String resolve(final String actionTarget, final VoiceIvrSession session) {
        if (StringUtils.isBlank(actionTarget)) {
            return null;
        }
        if (actionTarget.startsWith("+")) {
            return actionTarget;
        }
        final List<String> targets = new ArrayList<>();
        final String officerMobile = resolveAssignedOfficerMobile(actionTarget, session);
        if (StringUtils.isNotBlank(officerMobile)) {
            targets.add(officerMobile);
        }
        final String departmentNumber = resolveDepartmentNumber(actionTarget);
        if (StringUtils.isNotBlank(departmentNumber) && !targets.contains(departmentNumber)) {
            targets.add(departmentNumber);
        }
        if (targets.isEmpty()) {
            return StringUtils.startsWith(actionTarget, "+") ? actionTarget : null;
        }
        return String.join(",", targets);
    }

    private String resolveAssignedOfficerMobile(final String actionTarget, final VoiceIvrSession session) {
        if (session == null || (!"LOANS".equalsIgnoreCase(actionTarget) && !"SUPPORT".equalsIgnoreCase(actionTarget))) {
            return null;
        }
        final Client client = session.getClient();
        if (client == null) {
            return null;
        }
        final Staff officer = client.getStaff();
        if (officer == null || StringUtils.isBlank(officer.mobileNo())) {
            return null;
        }
        return phoneNumberNormalizer.normalize(officer.mobileNo());
    }

    private String resolveDepartmentNumber(final String actionTarget) {
        return switch (actionTarget) {
            case "LOANS" -> properties.getVoice().getLoansDepartmentNumber();
            case "SUPPORT" -> properties.getVoice().getSupportDepartmentNumber();
            case "STAFF" -> properties.getVoice().getInternalDepartmentNumber();
            default -> actionTarget.startsWith("+") ? actionTarget : null;
        };
    }
}

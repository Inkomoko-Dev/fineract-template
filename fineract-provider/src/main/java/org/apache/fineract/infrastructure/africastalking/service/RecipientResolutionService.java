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
package org.apache.fineract.infrastructure.africastalking.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.data.ResolvedRecipientData;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecipientResolutionService {

    private final PhoneNumberNormalizer phoneNumberNormalizer;
    private final ClientRepository clientRepository;
    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public ResolvedRecipientData resolve(final String rawPhoneNumber) {
        final String normalizedPhoneNumber = phoneNumberNormalizer.normalize(rawPhoneNumber);
        if (StringUtils.isBlank(normalizedPhoneNumber)) {
            return ResolvedRecipientData.unknown(null);
        }
        final List<String> lookupVariants = phoneNumberNormalizer.lookupVariants(rawPhoneNumber);
        final List<Client> clients = clientRepository.findByMobileNumbers(lookupVariants);
        if (!clients.isEmpty()) {
            return ResolvedRecipientData.client(normalizedPhoneNumber, clients.get(0).getId());
        }
        final List<Staff> staffMembers = staffRepository.findByMobileNumbers(lookupVariants);
        if (!staffMembers.isEmpty()) {
            return ResolvedRecipientData.staff(normalizedPhoneNumber, staffMembers.get(0).getId());
        }
        return ResolvedRecipientData.unknown(normalizedPhoneNumber);
    }
}

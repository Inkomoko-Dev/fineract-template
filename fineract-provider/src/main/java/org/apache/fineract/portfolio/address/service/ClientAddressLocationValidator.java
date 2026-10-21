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
package org.apache.fineract.portfolio.address.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.address.domain.Address;
import org.apache.fineract.portfolio.client.domain.ClientAddress;
import org.apache.fineract.portfolio.client.domain.ClientAddressRepositoryWrapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientAddressLocationValidator {

    public static final String ERROR_CODE = "error.msg.client.address.location.required";
    public static final String ERROR_MESSAGE = "Location is required";
    public static final String VALIDATION_CODE = "validation.msg.Address.location.cannot.be.blank";

    private final ClientAddressRepositoryWrapper clientAddressRepositoryWrapper;

    public void validateClientHasLocation(final Long clientId) {
        if (clientId == null) {
            return;
        }
        resolveRequiredLocation(clientId);
    }

    public String resolveRequiredLocation(final Long clientId) {
        final List<ClientAddress> addresses = clientAddressRepositoryWrapper.findAddressesForClient(clientId);
        final String location = firstUsableLocation(addresses.stream().filter(ClientAddress::isIs_active))
                .orElseGet(() -> firstUsableLocation(addresses.stream()).orElse(null));
        if (isMissingLocation(location)) {
            throw new GeneralPlatformDomainRuleException(ERROR_CODE, ERROR_MESSAGE);
        }
        return location.trim();
    }

    private static Optional<String> firstUsableLocation(final Stream<ClientAddress> addresses) {
        return addresses.map(ClientAddress::getAddress).filter(Objects::nonNull).map(Address::getLocation)
                .filter(value -> !isMissingLocation(value)).findFirst();
    }

    public static boolean isMissingLocation(final String location) {
        return StringUtils.isBlank(location) || "N/A".equalsIgnoreCase(location.trim());
    }
}

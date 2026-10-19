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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.address.domain.Address;
import org.apache.fineract.portfolio.client.domain.ClientAddress;
import org.apache.fineract.portfolio.client.domain.ClientAddressRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientAddressLocationValidatorTest {

    @Mock
    private ClientAddressRepositoryWrapper clientAddressRepositoryWrapper;
    @Mock
    private ClientAddress clientAddress;
    @Mock
    private Address address;

    private ClientAddressLocationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClientAddressLocationValidator(clientAddressRepositoryWrapper);
    }

    @Test
    void skipsGroupLoansWithoutAClient() {
        assertDoesNotThrow(() -> validator.validateClientHasLocation(null));
    }

    @Test
    void rejectsClientWithNoAddress() {
        when(clientAddressRepositoryWrapper.findAddressesForClient(9L)).thenReturn(List.of());
        final GeneralPlatformDomainRuleException exception = assertThrows(GeneralPlatformDomainRuleException.class,
                () -> validator.validateClientHasLocation(9L));
        assertEquals(ClientAddressLocationValidator.ERROR_MESSAGE, exception.getDefaultUserMessage());
        assertEquals(ClientAddressLocationValidator.ERROR_CODE, exception.getGlobalisationMessageCode());
    }

    @Test
    void rejectsPlaceholderLocation() {
        when(address.getLocation()).thenReturn("N/A");
        when(clientAddress.getAddress()).thenReturn(address);
        when(clientAddressRepositoryWrapper.findAddressesForClient(9L)).thenReturn(List.of(clientAddress));
        assertThrows(GeneralPlatformDomainRuleException.class, () -> validator.resolveRequiredLocation(9L));
    }

    @Test
    void returnsTrimmedLocation() {
        when(address.getLocation()).thenReturn("  Nairobi  ");
        when(clientAddress.getAddress()).thenReturn(address);
        when(clientAddressRepositoryWrapper.findAddressesForClient(9L)).thenReturn(List.of(clientAddress));
        assertEquals("Nairobi", validator.resolveRequiredLocation(9L));
    }

    @Test
    void treatsBlankAndPlaceholderAsMissing() {
        assertTrue(ClientAddressLocationValidator.isMissingLocation(null));
        assertTrue(ClientAddressLocationValidator.isMissingLocation("  "));
        assertTrue(ClientAddressLocationValidator.isMissingLocation("n/a"));
    }

    @Test
    void prefersActiveAddressOverInactive() {
        final ClientAddress inactive = mock(ClientAddress.class);
        final Address inactiveAddress = mock(Address.class);
        when(inactive.isIs_active()).thenReturn(false);
        lenient().when(inactive.getAddress()).thenReturn(inactiveAddress);
        lenient().when(inactiveAddress.getLocation()).thenReturn("Kigali");
        when(clientAddress.isIs_active()).thenReturn(true);
        when(address.getLocation()).thenReturn("Nairobi");
        when(clientAddress.getAddress()).thenReturn(address);
        when(clientAddressRepositoryWrapper.findAddressesForClient(9L)).thenReturn(List.of(inactive, clientAddress));
        assertEquals("Nairobi", validator.resolveRequiredLocation(9L));
    }
}

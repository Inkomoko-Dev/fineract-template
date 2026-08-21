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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.portfolio.client.domain.ClientOtherInfo;
import org.apache.fineract.portfolio.client.domain.ClientOtherInfoRepository;
import org.apache.fineract.portfolio.client.domain.RefBank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientBankDetailsResolverTest {

    private static final Integer PAYMENT_TO_CLIENT = 1;
    private static final Integer PAYMENT_TO_SUPPLIER = 2;
    private static final Long CLIENT_ID = 42L;

    @Mock
    private ClientOtherInfoRepository clientOtherInfoRepository;

    private ClientBankDetailsResolver resolver;

    @BeforeEach
    void setUp() {
        this.resolver = new ClientBankDetailsResolver(this.clientOtherInfoRepository);
    }

    private ClientOtherInfo storedOtherInfo(final String telephone, final String accountNumber, final String freeTextBankName,
            final String refBankName) {
        final ClientOtherInfo clientOtherInfo = mock(ClientOtherInfo.class);
        when(clientOtherInfo.getTelephoneNo()).thenReturn(telephone);
        when(clientOtherInfo.getBankAccountNumber()).thenReturn(accountNumber);
        if (refBankName == null) {
            when(clientOtherInfo.getBank()).thenReturn(null);
            when(clientOtherInfo.getBankName()).thenReturn(freeTextBankName);
        } else {
            final RefBank refBank = new RefBank("KCB", refBankName, "South Sudan");
            when(clientOtherInfo.getBank()).thenReturn(refBank);
        }
        return clientOtherInfo;
    }

    @Test
    void fallsBackToStoredBankDetailsWhenPayloadOmitsThem() {
        final ClientOtherInfo storedClientOtherInfo = storedOtherInfo("+211920000000", "1234567890", null, "Kenya Commercial Bank South Sudan");
        when(this.clientOtherInfoRepository.getByClientId(CLIENT_ID)).thenReturn(storedClientOtherInfo);

        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(CLIENT_ID, PAYMENT_TO_CLIENT, null,
                null, null);

        assertEquals("+211920000000", resolved.getClientPhoneNumber());
        assertEquals("1234567890", resolved.getClientAccountNumber());
        assertEquals("Kenya Commercial Bank South Sudan", resolved.getClientBankName());
    }

    @Test
    void fallsBackToFreeTextBankNameWhenNoReferenceBankIsLinked() {
        final ClientOtherInfo storedClientOtherInfo = storedOtherInfo("+211920000000", "1234567890", "Ivory Bank", null);
        when(this.clientOtherInfoRepository.getByClientId(CLIENT_ID)).thenReturn(storedClientOtherInfo);

        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(CLIENT_ID, PAYMENT_TO_CLIENT, null,
                null, null);

        assertEquals("Ivory Bank", resolved.getClientBankName());
    }

    @Test
    void payloadValuesWinOverStoredBankDetails() {
        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(CLIENT_ID, PAYMENT_TO_CLIENT,
                "+211911111111", "9999999999", "Eden Commercial Bank");

        assertEquals("+211911111111", resolved.getClientPhoneNumber());
        assertEquals("9999999999", resolved.getClientAccountNumber());
        assertEquals("Eden Commercial Bank", resolved.getClientBankName());
    }

    @Test
    void fillsOnlyTheBlankFields() {
        final ClientOtherInfo storedClientOtherInfo = storedOtherInfo("+211920000000", "1234567890", "Ivory Bank", null);
        when(this.clientOtherInfoRepository.getByClientId(CLIENT_ID)).thenReturn(storedClientOtherInfo);

        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(CLIENT_ID, PAYMENT_TO_CLIENT, null,
                "9999999999", null);

        assertEquals("+211920000000", resolved.getClientPhoneNumber());
        assertEquals("9999999999", resolved.getClientAccountNumber());
        assertEquals("Ivory Bank", resolved.getClientBankName());
    }

    @Test
    void neverFallsBackForSupplierPayments() {
        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(CLIENT_ID, PAYMENT_TO_SUPPLIER, null,
                null, null);

        assertNull(resolved.getClientPhoneNumber());
        assertNull(resolved.getClientAccountNumber());
        assertNull(resolved.getClientBankName());
        verify(this.clientOtherInfoRepository, never()).getByClientId(anyLong());
    }

    @Test
    void doesNotQueryTheClientRecordWhenThePayloadIsAlreadyComplete() {
        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(CLIENT_ID, PAYMENT_TO_CLIENT,
                "+211911111111", "9999999999", "Eden Commercial Bank");

        assertEquals("Eden Commercial Bank", resolved.getClientBankName());
        verify(this.clientOtherInfoRepository, never()).getByClientId(anyLong());
    }

    @Test
    void keepsPayloadBlankWhenTheClientHasNoOtherInfoRecord() {
        when(this.clientOtherInfoRepository.getByClientId(CLIENT_ID)).thenReturn(null);

        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(CLIENT_ID, PAYMENT_TO_CLIENT, null,
                null, null);

        assertNull(resolved.getClientAccountNumber());
        assertNull(resolved.getClientBankName());
    }

    @Test
    void treatsNullPaymentToAsPaymentToClient() {
        final ClientOtherInfo storedClientOtherInfo = storedOtherInfo("+211920000000", "1234567890", "Ivory Bank", null);
        when(this.clientOtherInfoRepository.getByClientId(CLIENT_ID)).thenReturn(storedClientOtherInfo);

        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(CLIENT_ID, null, null, null, null);

        assertEquals("1234567890", resolved.getClientAccountNumber());
        assertEquals("Ivory Bank", resolved.getClientBankName());
    }

    @Test
    void skipsFallbackForGroupLoansWithoutAClient() {
        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(null, PAYMENT_TO_CLIENT, null, null,
                null);

        assertNull(resolved.getClientAccountNumber());
        verify(this.clientOtherInfoRepository, never()).getByClientId(anyLong());
    }

    @Test
    void treatsWhitespaceOnlyStoredValuesAsMissing() {
        final ClientOtherInfo storedClientOtherInfo = storedOtherInfo(" ", " ", " ", null);
        when(this.clientOtherInfoRepository.getByClientId(CLIENT_ID)).thenReturn(storedClientOtherInfo);

        final ClientBankDetailsResolver.ResolvedClientPaymentDetails resolved = this.resolver.resolve(CLIENT_ID, PAYMENT_TO_CLIENT, null,
                null, null);

        assertNull(resolved.getClientAccountNumber());
        assertNull(resolved.getClientBankName());
    }
}

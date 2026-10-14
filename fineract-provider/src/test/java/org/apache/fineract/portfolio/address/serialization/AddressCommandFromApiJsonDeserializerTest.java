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
package org.apache.fineract.portfolio.address.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.address.data.FieldConfigurationData;
import org.apache.fineract.portfolio.address.service.ClientAddressLocationValidator;
import org.apache.fineract.portfolio.address.service.FieldConfigurationReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressCommandFromApiJsonDeserializerTest {

    @Mock
    private FieldConfigurationReadPlatformService fieldConfigurationReadPlatformService;

    private AddressCommandFromApiJsonDeserializer deserializer;

    @BeforeEach
    void setUp() {
        deserializer = new AddressCommandFromApiJsonDeserializer(new FromJsonHelper(), fieldConfigurationReadPlatformService);
        when(fieldConfigurationReadPlatformService.retrieveFieldConfigurationList("ADDRESS")).thenReturn(List.of(
                FieldConfigurationData.instance(1L, "ADDRESS", "CLIENT", "street", true, false, ""),
                FieldConfigurationData.instance(2L, "ADDRESS", "CLIENT", "location", true, false, "")));
    }

    @Test
    void createRejectsMissingLocation() {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> deserializer.validateForCreate(validAddressJson(null), true));
        assertLocationRequired(exception.getErrors());
    }

    @Test
    void createRejectsBlankLocation() {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> deserializer.validateForCreate(
                        "{\"addressId\":14,\"street\":\"Main\",\"physicalAddressDistrict\":\"d\",\"physicalAddressSector\":\"s\",\"physicalAddressCell\":\"c\",\"location\":\"   \"}",
                        false));
        assertLocationRequired(exception.getErrors());
    }

    @Test
    void updateRejectsMissingLocation() {
        final PlatformApiDataValidationException exception = assertThrows(PlatformApiDataValidationException.class,
                () -> deserializer.validateForUpdate("{\"addressId\":14,\"street\":\"Main\"}"));
        assertLocationRequired(exception.getErrors());
    }

    @Test
    void createAcceptsLocation() {
        assertDoesNotThrow(() -> deserializer.validateForCreate(validAddressJson("Kigali"), true));
    }

    @Test
    void updateAcceptsLocation() {
        assertDoesNotThrow(() -> deserializer.validateForUpdate("{\"addressId\":14,\"street\":\"Main\",\"location\":\"Nairobi\"}"));
    }

    private static String validAddressJson(final String location) {
        final StringBuilder json = new StringBuilder(
                "{\"addressTypeId\":1,\"street\":\"Main\",\"physicalAddressDistrict\":\"d\",\"physicalAddressSector\":\"s\",\"physicalAddressCell\":\"c\"");
        if (location != null) {
            json.append(",\"location\":\"").append(location).append('"');
        }
        json.append('}');
        return json.toString();
    }

    private static void assertLocationRequired(final List<ApiParameterError> errors) {
        assertTrue(errors.stream().anyMatch(error -> "location".equals(error.getParameterName())));
        assertEquals(ClientAddressLocationValidator.ERROR_MESSAGE, errors.stream().filter(error -> "location".equals(error.getParameterName()))
                .findFirst().orElseThrow().getDefaultUserMessage());
        assertEquals(ClientAddressLocationValidator.VALIDATION_CODE,
                errors.stream().filter(error -> "location".equals(error.getParameterName())).findFirst().orElseThrow()
                        .getUserMessageGlobalisationCode());
    }
}

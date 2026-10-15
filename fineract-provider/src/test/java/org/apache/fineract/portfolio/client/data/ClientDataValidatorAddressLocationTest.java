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
package org.apache.fineract.portfolio.client.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.address.service.ClientAddressLocationValidator;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientDataValidatorAddressLocationTest {

    private ClientDataValidator validator;

    @BeforeEach
    void setUp() {
        this.validator = new ClientDataValidator(new FromJsonHelper(), null);
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 15));
        businessDates.put(BusinessDateType.COB_DATE, LocalDate.of(2026, 10, 14));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    void createRejectsOmittedAddressArray() {
        assertLocationRequired(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(personJson(null))));
    }

    @Test
    void createRejectsEmptyAddressArray() {
        assertLocationRequired(
                assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(personJson("\"address\":[]"))));
    }

    @Test
    void createRejectsAddressWithoutLocation() {
        assertLocationRequired(assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(personJson("\"address\":[{\"addressTypeId\":1,\"street\":\"Main\"}]"))));
    }

    @Test
    void createRejectsPlaceholderLocation() {
        assertLocationRequired(assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(personJson("\"address\":[{\"addressTypeId\":1,\"location\":\"N/A\"}]"))));
    }

    @Test
    void entityCreateRejectsOmittedAddressArray() {
        assertLocationRequired(assertThrows(PlatformApiDataValidationException.class, () -> validator.validateForCreate(
                "{\"officeId\":1,\"fullname\":\"Acme Ltd\",\"legalFormId\":2,\"externalId\":\"ent-1\",\"active\":false,\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\"}")));
    }

    @Test
    void createAcceptsAddressWithLocation() {
        assertDoesNotThrow(() -> validator.validateForCreate(
                personJson("\"address\":[{\"addressTypeId\":1,\"street\":\"Main\",\"location\":\"Kigali\"}]")));
    }

    private static String personJson(final String extraJson) {
        final StringBuilder json = new StringBuilder(
                "{\"officeId\":1,\"firstname\":\"Amina\",\"lastname\":\"Uwase\",\"legalFormId\":1,\"externalId\":\"cli-1\","
                        + "\"active\":false,\"genderId\":1,\"dateOfBirth\":\"1990-01-01\",\"locale\":\"en\",\"dateFormat\":\"yyyy-MM-dd\"");
        if (extraJson != null) {
            json.append(',').append(extraJson);
        }
        return json.append('}').toString();
    }

    private static void assertLocationRequired(final PlatformApiDataValidationException exception) {
        final List<ApiParameterError> errors = exception.getErrors();
        assertTrue(errors.stream().anyMatch(error -> ClientApiConstants.address.equals(error.getParameterName())
                && ClientAddressLocationValidator.VALIDATION_CODE.equals(error.getUserMessageGlobalisationCode())));
        assertEquals(ClientAddressLocationValidator.ERROR_MESSAGE,
                errors.stream().filter(error -> ClientAddressLocationValidator.VALIDATION_CODE.equals(error.getUserMessageGlobalisationCode()))
                        .findFirst().orElseThrow().getDefaultUserMessage());
    }
}

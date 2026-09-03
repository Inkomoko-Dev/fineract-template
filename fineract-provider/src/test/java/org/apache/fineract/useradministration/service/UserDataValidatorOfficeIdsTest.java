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
package org.apache.fineract.useradministration.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicy;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class UserDataValidatorOfficeIdsTest {

    private UserDataValidator validator;

    @BeforeEach
    public void setUp() {
        final PasswordValidationPolicy policy = Mockito.mock(PasswordValidationPolicy.class);
        Mockito.when(policy.getRegex()).thenReturn(".*");
        Mockito.when(policy.getDescription()).thenReturn("anything");
        final PasswordValidationPolicyRepository repository = Mockito.mock(PasswordValidationPolicyRepository.class);
        Mockito.when(repository.findActivePasswordValidationPolicy()).thenReturn(policy);
        this.validator = new UserDataValidator(new FromJsonHelper(), repository);
    }

    @Test
    @DisplayName("A user may be created with additional offices")
    public void createAcceptsOfficeIds() {
        assertDoesNotThrow(() -> this.validator.validateForCreate(createJson(List.of(5, 6))));
    }

    @Test
    @DisplayName("A user may be updated with additional offices")
    public void updateAcceptsOfficeIds() {
        assertDoesNotThrow(() -> this.validator.validateForUpdate(updateJson(List.of(5, 6))));
    }

    @Test
    @DisplayName("Clearing every additional office is allowed")
    public void updateAcceptsAnEmptyOfficeIdList() {
        assertDoesNotThrow(() -> this.validator.validateForUpdate(updateJson(List.of())));
    }

    @Test
    @DisplayName("An additional office must be a real office identifier")
    public void rejectsNonPositiveOfficeIds() {
        final PlatformApiDataValidationException e = assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateForUpdate(updateJson(List.of(0))));

        assertTrue(e.getErrors().stream().anyMatch(error -> "officeIds".equals(error.getParameterName())));
    }

    @Test
    @DisplayName("An unknown parameter is still rejected")
    public void stillRejectsUnsupportedParameters() {
        assertThrows(UnsupportedParameterException.class,
                () -> this.validator.validateForUpdate(new Gson().toJson(Map.of("notes", "n", "officeIdz", List.of(5)))));
    }

    private String updateJson(final List<Integer> officeIds) {
        final Map<String, Object> json = new LinkedHashMap<>();
        json.put("notes", "Reassigned to the Kigali branches");
        json.put("officeIds", officeIds);
        return new Gson().toJson(json);
    }

    private String createJson(final List<Integer> officeIds) {
        final Map<String, Object> json = new LinkedHashMap<>();
        json.put("username", "jdoe");
        json.put("firstname", "Jane");
        json.put("lastname", "Doe");
        json.put("email", "jdoe@example.com");
        json.put("officeId", 1);
        json.put("sendPasswordToEmail", false);
        json.put("password", "Password1");
        json.put("repeatPassword", "Password1");
        json.put("roles", List.of(1));
        json.put("notes", "Joining the Kigali branches");
        json.put("officeIds", officeIds);
        return new Gson().toJson(json);
    }
}

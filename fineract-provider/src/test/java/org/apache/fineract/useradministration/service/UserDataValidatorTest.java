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

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicy;
import org.apache.fineract.useradministration.domain.PasswordValidationPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UserDataValidatorTest {

    private UserDataValidator validator;

    @BeforeEach
    void setUp() {
        FromJsonHelper fromJsonHelper = new FromJsonHelper();
        PasswordValidationPolicyRepository policyRepo = Mockito.mock(PasswordValidationPolicyRepository.class);
        PasswordValidationPolicy policy = Mockito.mock(PasswordValidationPolicy.class);
        Mockito.when(policy.getRegex()).thenReturn("^.{6,50}$");
        Mockito.when(policy.getDescription()).thenReturn("Password must be 6 to 50 characters");
        Mockito.when(policyRepo.findActivePasswordValidationPolicy()).thenReturn(policy);
        validator = new UserDataValidator(fromJsonHelper, policyRepo);
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    private String validCreateJson(String notesFragment) {
        return "{"
            + "\"username\": \"testuser123\","
            + "\"firstname\": \"John\","
            + "\"lastname\": \"Doe\","
            + "\"email\": \"john@example.com\","
            + "\"officeId\": 1,"
            + "\"roles\": [\"1\"],"
            + "\"sendPasswordToEmail\": true"
            + (notesFragment != null ? ", " + notesFragment : "")
            + "}";
    }

    private String validUpdateJson(String notesFragment) {
        return "{"
            + "\"firstname\": \"Jane\""
            + (notesFragment != null ? ", " + notesFragment : "")
            + "}";
    }

    private String deleteJson(String notesFragment) {
        if (notesFragment == null) {
            return "{}";
        }
        return "{" + notesFragment + "}";
    }

    private boolean hasNotesError(PlatformApiDataValidationException ex) {
        return ex.getErrors().stream()
            .anyMatch(e -> e.getParameterName().equals("notes"));
    }

    // ─── Create User: Notes Validation ────────────────────────────────

    @Nested
    @DisplayName("validateForCreate — notes field")
    class CreateValidation {

        @Test
        @DisplayName("RED: create without notes → validation error")
        void createWithoutNotes_shouldFail() {
            String json = validCreateJson(null);
            PlatformApiDataValidationException ex = assertThrows(
                PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(json)
            );
            assertTrue(hasNotesError(ex), "Expected validation error for 'notes' parameter");
        }

        @Test
        @DisplayName("RED: create with empty notes → validation error")
        void createWithEmptyNotes_shouldFail() {
            String json = validCreateJson("\"notes\": \"\"");
            PlatformApiDataValidationException ex = assertThrows(
                PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(json)
            );
            assertTrue(hasNotesError(ex), "Expected validation error for empty 'notes'");
        }

        @Test
        @DisplayName("RED: create with whitespace-only notes → validation error")
        void createWithBlankNotes_shouldFail() {
            String json = validCreateJson("\"notes\": \"   \"");
            PlatformApiDataValidationException ex = assertThrows(
                PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(json)
            );
            assertTrue(hasNotesError(ex), "Expected validation error for blank 'notes'");
        }

        @Test
        @DisplayName("RED: create with notes exceeding 500 chars → validation error")
        void createWithOversizedNotes_shouldFail() {
            String longNotes = "\"notes\": \"" + "A".repeat(501) + "\"";
            String json = validCreateJson(longNotes);
            PlatformApiDataValidationException ex = assertThrows(
                PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(json)
            );
            assertTrue(hasNotesError(ex), "Expected validation error for oversized 'notes'");
        }

        @Test
        @DisplayName("GREEN: create with valid notes → passes validation")
        void createWithValidNotes_shouldPass() {
            String json = validCreateJson("\"notes\": \"CGLT-564: New user onboarding\"");
            assertDoesNotThrow(() -> validator.validateForCreate(json));
        }
    }

    // ─── Update User: Notes Validation ────────────────────────────────

    @Nested
    @DisplayName("validateForUpdate — notes field")
    class UpdateValidation {

        @Test
        @DisplayName("RED: update without notes → validation error")
        void updateWithoutNotes_shouldFail() {
            String json = validUpdateJson(null);
            PlatformApiDataValidationException ex = assertThrows(
                PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate(json)
            );
            assertTrue(hasNotesError(ex), "Expected validation error for 'notes' parameter");
        }

        @Test
        @DisplayName("RED: update with empty notes → validation error")
        void updateWithEmptyNotes_shouldFail() {
            String json = validUpdateJson("\"notes\": \"\"");
            PlatformApiDataValidationException ex = assertThrows(
                PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate(json)
            );
            assertTrue(hasNotesError(ex), "Expected validation error for empty 'notes'");
        }

        @Test
        @DisplayName("GREEN: update with valid notes → passes validation")
        void updateWithValidNotes_shouldPass() {
            String json = validUpdateJson("\"notes\": \"CGLT-564: Role change per HR request\"");
            assertDoesNotThrow(() -> validator.validateForUpdate(json));
        }
    }

    // ─── Delete User: Notes Validation ────────────────────────────────

    @Nested
    @DisplayName("validateForDelete — notes field")
    class DeleteValidation {

        @Test
        @DisplayName("RED: delete without notes → validation error")
        void deleteWithoutNotes_shouldFail() {
            String json = deleteJson(null);
            PlatformApiDataValidationException ex = assertThrows(
                PlatformApiDataValidationException.class,
                () -> validator.validateForDelete(json)
            );
            assertTrue(hasNotesError(ex), "Expected validation error for 'notes' parameter");
        }

        @Test
        @DisplayName("RED: delete with empty notes → validation error")
        void deleteWithEmptyNotes_shouldFail() {
            String json = deleteJson("\"notes\": \"\"");
            PlatformApiDataValidationException ex = assertThrows(
                PlatformApiDataValidationException.class,
                () -> validator.validateForDelete(json)
            );
            assertTrue(hasNotesError(ex), "Expected validation error for empty 'notes'");
        }

        @Test
        @DisplayName("RED: delete with notes exceeding 500 chars → validation error")
        void deleteWithOversizedNotes_shouldFail() {
            String longNotes = "\"notes\": \"" + "X".repeat(501) + "\"";
            String json = deleteJson(longNotes);
            PlatformApiDataValidationException ex = assertThrows(
                PlatformApiDataValidationException.class,
                () -> validator.validateForDelete(json)
            );
            assertTrue(hasNotesError(ex), "Expected validation error for oversized 'notes'");
        }

        @Test
        @DisplayName("GREEN: delete with valid notes → passes validation")
        void deleteWithValidNotes_shouldPass() {
            String json = deleteJson("\"notes\": \"CGLT-564: User offboarding\"");
            assertDoesNotThrow(() -> validator.validateForDelete(json));
        }

        @Test
        @DisplayName("RED: delete with null/blank JSON body → InvalidJsonException")
        void deleteWithNullBody_shouldFail() {
            assertThrows(Exception.class, () -> validator.validateForDelete(null));
            assertThrows(Exception.class, () -> validator.validateForDelete(""));
            assertThrows(Exception.class, () -> validator.validateForDelete("   "));
        }
    }
}

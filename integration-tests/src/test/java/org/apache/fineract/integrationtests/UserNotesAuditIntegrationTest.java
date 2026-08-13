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
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.integrationtests.common.AuditHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class UserNotesAuditIntegrationTest {

    private RequestSpecification requestSpec;
    private ResponseSpecification responseSpec200;
    private ResponseSpecification responseSpec400;
    private AuditHelper auditHelper;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization",
                "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec200 = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.responseSpec400 = new ResponseSpecBuilder().expectStatusCode(400).build();
        this.auditHelper = new AuditHelper(this.requestSpec, this.responseSpec200);
    }

    // ─── Helper: build create-user JSON with/without notes ───────────

    private String createUserJson(int roleId, int staffId, String username, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"username\": \"").append(username).append("\",");
        sb.append("\"firstname\": \"Test\",");
        sb.append("\"lastname\": \"User\",");
        sb.append("\"email\": \"test@example.com\",");
        sb.append("\"officeId\": 1,");
        sb.append("\"staffId\": ").append(staffId).append(",");
        sb.append("\"roles\": [\"").append(roleId).append("\"],");
        sb.append("\"sendPasswordToEmail\": false");
        if (notes != null) {
            sb.append(", \"notes\": \"").append(notes).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String updateUserJson(String username, String notes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"username\": \"").append(username).append("\"");
        if (notes != null) {
            sb.append(", \"notes\": \"").append(notes).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String deleteUserJson(String notes) {
        if (notes == null) {
            return "{}";
        }
        return "{\"notes\": \"" + notes + "\"}";
    }

    // ─── RED: Create user without notes → 400 ────────────────────────

    @Test
    @DisplayName("RED: POST /users without notes returns 400 validation error")
    public void createUserWithoutNotes_shouldReturn400() {
        final Integer roleId = RolesHelper.createRole(requestSpec, responseSpec200);
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec200);
        String username = Utils.randomNameGenerator("User_", 5);

        String json = createUserJson(roleId, staffId, username, null);
        List errors = (List) Utils.performServerPost(requestSpec, responseSpec400,
                "/fineract-provider/api/v1/users?" + Utils.TENANT_IDENTIFIER,
                json, "errors");

        assertNotNull(errors);
        assertTrue(errors.size() > 0, "Expected at least one validation error");
        Map<String, Object> firstError = (Map<String, Object>) errors.get(0);
        assertEquals("notes", firstError.get("parameterName"));
    }

    // ─── GREEN: Create user with notes → 200 + audit has notes ───────

    @Test
    @DisplayName("GREEN: POST /users with notes succeeds and audit trail captures notes")
    public void createUserWithNotes_shouldSucceedAndAuditHasNotes() {
        final Integer roleId = RolesHelper.createRole(requestSpec, responseSpec200);
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec200);
        String username = Utils.randomNameGenerator("User_", 5);
        String expectedNotes = "CGLT-564: New user for QA team";

        String json = createUserJson(roleId, staffId, username, expectedNotes);
        Integer userId = Utils.performServerPost(requestSpec, responseSpec200,
                "/fineract-provider/api/v1/users?" + Utils.TENANT_IDENTIFIER,
                json, "resourceId");

        assertNotNull(userId, "User should be created");

        // Verify audit trail has the notes
        List<HashMap<String, Object>> audits = auditHelper.getAuditDetails(userId, "CREATE", "USER");
        assertNotNull(audits);
        assertTrue(audits.size() >= 1, "Expected at least one CREATE USER audit entry");
        assertEquals(expectedNotes, audits.get(0).get("notes"),
                "Audit trail notes should match the submitted value");

        // Cleanup
        UserHelper.deleteUser(requestSpec, responseSpec200, userId);
    }

    // ─── RED: Update user without notes → 400 ────────────────────────

    @Test
    @DisplayName("RED: PUT /users/{id} without notes returns 400 validation error")
    public void updateUserWithoutNotes_shouldReturn400() {
        final Integer roleId = RolesHelper.createRole(requestSpec, responseSpec200);
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec200);
        String username = Utils.randomNameGenerator("User_", 5);

        // Create user first (with notes)
        String createJson = createUserJson(roleId, staffId, username, "Initial creation note");
        Integer userId = Utils.performServerPost(requestSpec, responseSpec200,
                "/fineract-provider/api/v1/users?" + Utils.TENANT_IDENTIFIER,
                createJson, "resourceId");
        assertNotNull(userId);

        // Update without notes → should fail
        String newUsername = Utils.randomNameGenerator("User_", 5);
        String updateJson = updateUserJson(newUsername, null);
        List errors = (List) Utils.performServerPut(requestSpec, responseSpec400,
                "/fineract-provider/api/v1/users/" + userId + "?" + Utils.TENANT_IDENTIFIER,
                updateJson, "errors");

        assertNotNull(errors);
        assertTrue(errors.size() > 0);

        // Cleanup
        UserHelper.deleteUser(requestSpec, responseSpec200, userId);
    }

    // ─── GREEN: Update user with notes → 200 + audit has notes ───────

    @Test
    @DisplayName("GREEN: PUT /users/{id} with notes succeeds and audit captures notes")
    public void updateUserWithNotes_shouldSucceedAndAuditHasNotes() {
        final Integer roleId = RolesHelper.createRole(requestSpec, responseSpec200);
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec200);
        String username = Utils.randomNameGenerator("User_", 5);

        // Create user
        String createJson = createUserJson(roleId, staffId, username, "Creation note");
        Integer userId = Utils.performServerPost(requestSpec, responseSpec200,
                "/fineract-provider/api/v1/users?" + Utils.TENANT_IDENTIFIER,
                createJson, "resourceId");
        assertNotNull(userId);

        // Update with notes
        String newUsername = Utils.randomNameGenerator("User_", 5);
        String expectedNotes = "CGLT-564: Updated role per ticket HR-100";
        String updateJson = updateUserJson(newUsername, expectedNotes);
        Integer result = Utils.performServerPut(requestSpec, responseSpec200,
                "/fineract-provider/api/v1/users/" + userId + "?" + Utils.TENANT_IDENTIFIER,
                updateJson, "resourceId");
        assertNotNull(result);

        // Verify audit trail
        List<HashMap<String, Object>> audits = auditHelper.getAuditDetails(userId, "UPDATE", "USER");
        assertNotNull(audits);
        assertTrue(audits.size() >= 1);
        HashMap<String, Object> latestAudit = audits.get(0);
        assertEquals(expectedNotes, latestAudit.get("notes"));

        // Cleanup
        UserHelper.deleteUser(requestSpec, responseSpec200, userId);
    }

    // ─── RED: Delete user without notes → 400 ────────────────────────

    @Test
    @DisplayName("RED: DELETE /users/{id} without notes returns 400 validation error")
    public void deleteUserWithoutNotes_shouldReturn400() {
        final Integer roleId = RolesHelper.createRole(requestSpec, responseSpec200);
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec200);
        String username = Utils.randomNameGenerator("User_", 5);

        // Create user
        String createJson = createUserJson(roleId, staffId, username, "Creation note");
        Integer userId = Utils.performServerPost(requestSpec, responseSpec200,
                "/fineract-provider/api/v1/users?" + Utils.TENANT_IDENTIFIER,
                createJson, "resourceId");
        assertNotNull(userId);

        // Delete without notes → should fail
        String deleteUrl = "/fineract-provider/api/v1/users/" + userId + "?" + Utils.TENANT_IDENTIFIER;
        List errors = (List) Utils.performServerDelete(requestSpec, responseSpec400,
                deleteUrl, deleteUserJson(null), "errors");

        assertNotNull(errors);
        assertTrue(errors.size() > 0);

        // Cleanup (force delete)
        UserHelper.deleteUser(requestSpec, responseSpec200, userId);
    }

    // ─── GREEN: Delete user with notes → 200 + audit has notes ───────

    @Test
    @DisplayName("GREEN: DELETE /users/{id} with notes succeeds and audit captures notes")
    public void deleteUserWithNotes_shouldSucceedAndAuditHasNotes() {
        final Integer roleId = RolesHelper.createRole(requestSpec, responseSpec200);
        final Integer staffId = StaffHelper.createStaff(requestSpec, responseSpec200);
        String username = Utils.randomNameGenerator("User_", 5);

        // Create user
        String createJson = createUserJson(roleId, staffId, username, "Creation note");
        Integer userId = Utils.performServerPost(requestSpec, responseSpec200,
                "/fineract-provider/api/v1/users?" + Utils.TENANT_IDENTIFIER,
                createJson, "resourceId");
        assertNotNull(userId);

        // Delete with notes
        String expectedNotes = "CGLT-564: User offboarded per ticket HR-200";
        String deleteUrl = "/fineract-provider/api/v1/users/" + userId + "?" + Utils.TENANT_IDENTIFIER;
        Integer deletedId = Utils.performServerDelete(requestSpec, responseSpec200,
                deleteUrl, deleteUserJson(expectedNotes), "resourceId");
        assertNotNull(deletedId);

        // Verify audit trail
        List<HashMap<String, Object>> audits = auditHelper.getAuditDetails(userId, "DELETE", "USER");
        assertNotNull(audits);
        assertTrue(audits.size() >= 1);
        assertEquals(expectedNotes, audits.get(0).get("notes"),
                "Audit trail notes should match the value entered during delete");
    }
}

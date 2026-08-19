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
package org.apache.fineract.portfolio.self.security.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.api.UsersApiResource;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SelfUserApiResourceTest {

    private static final Long SELF_SERVICE_USER_ID = 7L;

    private UsersApiResource usersApiResource;
    private SelfUserApiResource selfUserApiResource;

    @BeforeEach
    void setUp() {
        usersApiResource = Mockito.mock(UsersApiResource.class);
        final PlatformSecurityContext context = Mockito.mock(PlatformSecurityContext.class);
        final AppUser appUser = Mockito.mock(AppUser.class);
        Mockito.when(appUser.getId()).thenReturn(SELF_SERVICE_USER_ID);
        Mockito.when(context.authenticatedUser()).thenReturn(appUser);
        selfUserApiResource = new SelfUserApiResource(usersApiResource, context, new FromJsonHelper());
    }

    @Test
    @DisplayName("a self service password change may carry the notes required by the audit trail")
    void notesAreAcceptedAndForwarded() {
        final String json = "{\"password\": \"Password1\", \"repeatPassword\": \"Password1\", \"notes\": \"CGLT-564 password reset\"}";
        Mockito.when(usersApiResource.update(SELF_SERVICE_USER_ID, json)).thenReturn("{\"resourceId\": 7}");

        final String result = selfUserApiResource.update(json);

        assertEquals("{\"resourceId\": 7}", result);
        Mockito.verify(usersApiResource).update(SELF_SERVICE_USER_ID, json);
    }

    @Test
    @DisplayName("a self service user still may not update anything beyond their password and the notes")
    void otherParametersAreStillRejected() {
        final String json = "{\"password\": \"Password1\", \"repeatPassword\": \"Password1\", \"username\": \"someoneelse\"}";

        assertThrows(UnsupportedParameterException.class, () -> selfUserApiResource.update(json));
        Mockito.verifyNoInteractions(usersApiResource);
    }

    @Test
    @DisplayName("an empty request body is rejected")
    void blankBodyIsRejected() {
        assertThrows(InvalidJsonException.class, () -> selfUserApiResource.update(""));
        Mockito.verifyNoInteractions(usersApiResource);
    }
}

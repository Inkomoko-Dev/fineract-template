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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;

public class ClientMigratedCreatePermissionTest {

    private final ClientDataValidator validator = new ClientDataValidator(new FromJsonHelper(), null);

    @Test
    public void creatingAMigratedClientNeedsTheCreateMigratedClientPermission() {
        final AppUser standardUser = mock(AppUser.class);
        doThrow(new NoAuthorizationException("User has no authority to: CREATE_MIGRATED_CLIENT")).when(standardUser)
                .validateHasPermissionTo(ClientApiConstants.CREATE_MIGRATED_CLIENT_PERMISSION);

        assertThatThrownBy(() -> this.validator.validateMayCreateMigrated("{\"officeId\":1,\"migrated\":true}", standardUser))
                .isInstanceOf(NoAuthorizationException.class);
    }

    @Test
    public void aUserWithThePermissionCanCreateAMigratedClient() {
        final AppUser migrationUser = mock(AppUser.class);

        this.validator.validateMayCreateMigrated("{\"officeId\":1,\"migrated\":true}", migrationUser);

        verify(migrationUser).validateHasPermissionTo(ClientApiConstants.CREATE_MIGRATED_CLIENT_PERMISSION);
    }

    @Test
    public void creatingAnOrdinaryClientNeedsNoExtraPermission() {
        final AppUser standardUser = mock(AppUser.class);

        this.validator.validateMayCreateMigrated("{\"officeId\":1}", standardUser);
        this.validator.validateMayCreateMigrated("{\"officeId\":1,\"migrated\":false}", standardUser);

        verify(standardUser, never()).validateHasPermissionTo(anyString());
    }
}

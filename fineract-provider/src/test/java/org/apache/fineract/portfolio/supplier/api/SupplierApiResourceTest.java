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
package org.apache.fineract.portfolio.supplier.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.supplier.data.SupplierApiConstants;
import org.apache.fineract.portfolio.supplier.data.SupplierData;
import org.apache.fineract.portfolio.supplier.data.SupplierTemplateData;
import org.apache.fineract.portfolio.supplier.service.SupplierReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierApiResourceTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private SupplierReadPlatformService readPlatformService;

    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Mock
    private DefaultToApiJsonSerializer<SupplierData> toApiJsonSerializer;

    @Mock
    private AppUser appUser;

    @InjectMocks
    private SupplierApiResource underTest;

    @BeforeEach
    void setUp() {
        given(this.context.authenticatedUser()).willReturn(this.appUser);
        given(this.toApiJsonSerializer.serialize(any())).willReturn("{}");
    }

    @Test
    void callbackRequiresCreatePermission() {
        doNothing().when(this.appUser).validateHasPermissionTo("CREATE_SUPPLIER");
        final Map<String, Object> changes = new HashMap<>();
        changes.put(SupplierApiConstants.CREATED, Boolean.TRUE);
        final CommandProcessingResult result = CommandProcessingResult.withChanges(1L, changes);
        given(this.commandsSourceWritePlatformService.logCommandSource(any())).willReturn(result);

        final Response response = underTest.callback("{}");

        assertThat(response.getStatus()).isEqualTo(Status.CREATED.getStatusCode());
        verify(this.commandsSourceWritePlatformService).logCommandSource(any());
    }

    @Test
    void callbackReturnsOkForUpdate() {
        doNothing().when(this.appUser).validateHasPermissionTo("CREATE_SUPPLIER");
        final Map<String, Object> changes = new HashMap<>();
        changes.put(SupplierApiConstants.CREATED, Boolean.FALSE);
        final CommandProcessingResult result = CommandProcessingResult.withChanges(1L, changes);
        given(this.commandsSourceWritePlatformService.logCommandSource(any())).willReturn(result);

        final Response response = underTest.callback("{}");

        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
    }

    @Test
    void callbackDeniedWithoutPermission() {
        doThrow(NoAuthorizationException.class).when(this.appUser).validateHasPermissionTo("CREATE_SUPPLIER");

        assertThatThrownBy(() -> underTest.callback("{}")).isInstanceOf(NoAuthorizationException.class);
        verifyNoInteractions(this.commandsSourceWritePlatformService);
    }

    @Test
    void listRequiresReadPermission() {
        doNothing().when(this.appUser).validateHasReadPermission(SupplierApiConstants.ENTITY_NAME);
        given(this.readPlatformService.retrieveAllPaged(any(), any(), any(), any(), any(), eq(0), eq(15)))
                .willReturn(new Page<>(Collections.emptyList(), 0));

        underTest.retrieveAll(null, null, null, null, null, 0, 15);

        verify(this.readPlatformService).retrieveAllPaged(any(), any(), any(), any(), any(), eq(0), eq(15));
    }

    @Test
    void listDeniedWithoutPermission() {
        doThrow(NoAuthorizationException.class).when(this.appUser).validateHasReadPermission(SupplierApiConstants.ENTITY_NAME);

        assertThatThrownBy(() -> underTest.retrieveAll(null, null, null, null, null, 0, 15))
                .isInstanceOf(NoAuthorizationException.class);
        verifyNoInteractions(this.readPlatformService);
    }

    @Test
    void retrieveOneRequiresReadPermission() {
        doNothing().when(this.appUser).validateHasReadPermission(SupplierApiConstants.ENTITY_NAME);
        given(this.readPlatformService.retrieveOne(3L)).willReturn(org.mockito.Mockito.mock(SupplierData.class));

        underTest.retrieveOne(3L);

        verify(this.readPlatformService).retrieveOne(3L);
    }

    @Test
    void templateRequiresReadPermission() {
        doNothing().when(this.appUser).validateHasReadPermission(SupplierApiConstants.ENTITY_NAME);
        given(this.readPlatformService.retrieveTemplate()).willReturn(new SupplierTemplateData(Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()));

        underTest.retrieveTemplate();

        verify(this.readPlatformService).retrieveTemplate();
    }
}

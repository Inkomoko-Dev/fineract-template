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
package org.apache.fineract.portfolio.loanaccount.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionApiConstants;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementInstructionData;
import org.apache.fineract.portfolio.loanaccount.domain.DisbursementInstructionStatus;
import org.apache.fineract.portfolio.loanaccount.service.DisbursementInstructionReadPlatformService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DisbursementInstructionApiResourceTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @Mock
    private DisbursementInstructionReadPlatformService readPlatformService;

    @Mock
    private DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;

    @Mock
    private DefaultToApiJsonSerializer<DisbursementInstructionData> instructionToApiJsonSerializer;

    @Mock
    private AppUser appUser;

    private DisbursementInstructionApiResource underTest;

    @BeforeEach
    void setUp() {
        this.underTest = new DisbursementInstructionApiResource(this.context, this.commandsSourceWritePlatformService,
                this.readPlatformService, this.toApiJsonSerializer, this.instructionToApiJsonSerializer);
        given(this.context.authenticatedUser()).willReturn(this.appUser);
    }

    @Test
    void createsDisbursementInstructionWhenAuthorized() {
        doNothing().when(this.appUser).validateHasPermissionTo(DisbursementInstructionApiConstants.PERMISSION_CODE);
        final Map<String, Object> changes = new HashMap<>();
        changes.put(DisbursementInstructionApiConstants.SUCCESS, Boolean.TRUE);
        changes.put(DisbursementInstructionApiConstants.LOAN_ID, 10L);
        changes.put(DisbursementInstructionApiConstants.INSTRUCTION_ID, 55L);
        changes.put(DisbursementInstructionApiConstants.REPLAYED, Boolean.FALSE);
        final CommandProcessingResult result = CommandProcessingResult.withChanges(55L, changes);
        given(this.commandsSourceWritePlatformService.logCommandSource(any())).willReturn(result);
        given(this.toApiJsonSerializer.serialize(any())).willReturn("{\"success\":true,\"instructionId\":55}");

        final Response response = this.underTest.createDisbursementInstruction("idem-1",
                "{\"loanAccountNo\":\"000000001\",\"sourceSystem\":\"KIFIYA\",\"supplierExternalId\":\"SUP-001\"}");

        assertThat(response.getStatus()).isEqualTo(Status.CREATED.getStatusCode());
        verify(this.commandsSourceWritePlatformService).logCommandSource(argThat((CommandWrapper cmd) -> cmd.getJson().contains("idem-1")));
        verify(this.appUser).validateHasPermissionTo(DisbursementInstructionApiConstants.PERMISSION_CODE);
    }

    @Test
    void returnsOkWhenReplay() {
        doNothing().when(this.appUser).validateHasPermissionTo(DisbursementInstructionApiConstants.PERMISSION_CODE);
        final Map<String, Object> changes = new HashMap<>();
        changes.put(DisbursementInstructionApiConstants.SUCCESS, Boolean.TRUE);
        changes.put(DisbursementInstructionApiConstants.INSTRUCTION_ID, 55L);
        changes.put(DisbursementInstructionApiConstants.REPLAYED, Boolean.TRUE);
        final CommandProcessingResult result = CommandProcessingResult.withChanges(55L, changes);
        given(this.commandsSourceWritePlatformService.logCommandSource(any())).willReturn(result);
        given(this.toApiJsonSerializer.serialize(any())).willReturn("{\"replayed\":true}");

        final Response response = this.underTest.createDisbursementInstruction("idem-1",
                "{\"loanAccountNo\":\"000000001\",\"sourceSystem\":\"KIFIYA\",\"supplierExternalId\":\"SUP-001\"}");

        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
    }

    @Test
    void rejectsMissingIdempotencyKeyHeader() {
        doNothing().when(this.appUser).validateHasPermissionTo(DisbursementInstructionApiConstants.PERMISSION_CODE);

        assertThatThrownBy(() -> this.underTest.createDisbursementInstruction("  ", "{}"))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void injectsIdempotencyKeyIntoJson() {
        assertThat(DisbursementInstructionApiResource.injectIdempotencyKey("{\"loanAccountNo\":\"1\"}", "abc-123"))
                .contains("\"idempotencyKey\":\"abc-123\"");
    }

    @Test
    void retrievesInstructionByIdWhenAuthorized() {
        doNothing().when(this.appUser).validateHasPermissionTo(DisbursementInstructionApiConstants.PERMISSION_CODE);
        final DisbursementInstructionData data = new DisbursementInstructionData(55L, 10L, "KIFIYA", 3L, "SUP-001",
                DisbursementInstructionStatus.PENDING_DISBURSEMENT, "key-1", 9L, null, null, null);
        given(this.readPlatformService.retrieveOne(55L)).willReturn(data);
        given(this.instructionToApiJsonSerializer.serialize(data)).willReturn("{\"id\":55,\"status\":\"PENDING_DISBURSEMENT\"}");

        final String response = this.underTest.retrieveOne(55L);

        assertThat(response).contains("PENDING_DISBURSEMENT");
        verify(this.readPlatformService).retrieveOne(55L);
    }

    @Test
    void rejectsWhenMissingPermission() {
        doThrow(new NoAuthorizationException("denied")).when(this.appUser)
                .validateHasPermissionTo(DisbursementInstructionApiConstants.PERMISSION_CODE);

        assertThatThrownBy(() -> this.underTest.createDisbursementInstruction("idem-1", "{}"))
                .isInstanceOf(NoAuthorizationException.class);
    }
}

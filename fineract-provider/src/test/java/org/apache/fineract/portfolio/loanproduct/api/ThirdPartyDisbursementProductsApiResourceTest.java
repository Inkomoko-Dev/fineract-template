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
package org.apache.fineract.portfolio.loanproduct.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductApiConstants;
import org.apache.fineract.portfolio.loanproduct.data.ThirdPartyDisbursementProductData;
import org.apache.fineract.portfolio.loanproduct.service.DisbursementPartnerAccessService;
import org.apache.fineract.portfolio.loanproduct.service.ThirdPartyDisbursementProductReadPlatformService;
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
class ThirdPartyDisbursementProductsApiResourceTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private ThirdPartyDisbursementProductReadPlatformService readPlatformService;

    @Mock
    private DisbursementPartnerAccessService disbursementPartnerAccessService;

    @Mock
    private DefaultToApiJsonSerializer<ThirdPartyDisbursementProductData> toApiJsonSerializer;

    @Mock
    private AppUser appUser;

    @InjectMocks
    private ThirdPartyDisbursementProductsApiResource underTest;

    @BeforeEach
    void setUp() {
        given(this.context.authenticatedUser()).willReturn(this.appUser);
        given(this.disbursementPartnerAccessService.resolveProviderCodeForUser(this.appUser)).willReturn(Optional.of("KIFIYA"));
    }

    @Test
    void listsFlaggedProductsUsingBoundProvider() {
        final Page<ThirdPartyDisbursementProductData> page = new Page<>(Collections.emptyList(), 0);
        given(this.readPlatformService.retrieveAll("KIFIYA", false, 0, 15)).willReturn(page);
        given(this.toApiJsonSerializer.serialize(page)).willReturn("{\"totalFilteredRecords\":0,\"pageItems\":[]}");

        final String response = this.underTest.retrieveAll(null, false, 0, 15);

        assertThat(response).contains("totalFilteredRecords");
        verify(this.appUser).validateHasPermissionTo(ThirdPartyDisbursementProductApiConstants.PERMISSION_CODE);
        verify(this.readPlatformService).retrieveAll("KIFIYA", false, 0, 15);
    }

    @Test
    void rejectsWhenPartnerBindingMissing() {
        given(this.disbursementPartnerAccessService.resolveProviderCodeForUser(this.appUser)).willReturn(Optional.empty());

        assertThatThrownBy(() -> this.underTest.retrieveAll("KIFIYA", null, null, null))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsWhenProviderQueryMismatchesBinding() {
        assertThatThrownBy(() -> this.underTest.retrieveAll("OTHER", null, null, null))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsWhenMissingPermission() {
        doThrow(new NoAuthorizationException("denied")).when(this.appUser)
                .validateHasPermissionTo(ThirdPartyDisbursementProductApiConstants.PERMISSION_CODE);

        assertThatThrownBy(() -> this.underTest.retrieveAll("KIFIYA", null, null, null))
                .isInstanceOf(NoAuthorizationException.class);
    }
}

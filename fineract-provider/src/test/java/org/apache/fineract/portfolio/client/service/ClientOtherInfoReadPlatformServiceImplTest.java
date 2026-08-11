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
package org.apache.fineract.portfolio.client.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.portfolio.client.data.ClientOtherInfoData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClientOtherInfoReadPlatformServiceImplTest {

    @Mock
    private CodeValueReadPlatformService codeValueReadPlatformService;

    private ClientOtherInfoReadPlatformServiceImpl readService;

    @BeforeEach
    void setUp() {
        this.readService = new ClientOtherInfoReadPlatformServiceImpl(null, null, this.codeValueReadPlatformService);
    }

    @Test
    void retrieveTemplateUsesCountryCodeForCountryOfOriginOptions() {
        final List<CodeValueData> nationalityOptions = List.of(CodeValueData.instance(1L, "Rwanda"));
        final List<CodeValueData> strataOptions = List.of(CodeValueData.instance(2L, "Refugee"));
        assertThat(ClientApiConstants.NATIONALITY_COUNTRY_OF_ORIGIN).isEqualTo(ClientApiConstants.COUNTRY);
        when(this.codeValueReadPlatformService.retrieveCodeValuesByCode(ClientApiConstants.NATIONALITY_COUNTRY_OF_ORIGIN))
                .thenReturn(nationalityOptions);
        when(this.codeValueReadPlatformService.retrieveCodeValuesByCode(ClientApiConstants.STRATA)).thenReturn(strataOptions);

        final ClientOtherInfoData template = this.readService.retrieveTemplate();

        assertThat(ReflectionTestUtils.getField(template, "nationalityOptions")).isEqualTo(nationalityOptions);
        verify(this.codeValueReadPlatformService).retrieveCodeValuesByCode(ClientApiConstants.NATIONALITY_COUNTRY_OF_ORIGIN);
    }
}

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.codes.exception.CodeValueNotFoundException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClientOtherInfoWritePlatformServiceImplTest {

    @Mock
    private CodeValueRepositoryWrapper codeValueRepository;

    @Mock
    private CodeValue codeValue;

    private ClientOtherInfoWritePlatformServiceImpl writeService;

    @BeforeEach
    void setUp() {
        this.writeService = new ClientOtherInfoWritePlatformServiceImpl(null, this.codeValueRepository, null, null, null, null, null);
    }

    @Test
    void findNationalityUsesCountryCode() {
        when(this.codeValueRepository.findOneByCodeNameAndIdWithNotFoundDetection(
                ClientApiConstants.NATIONALITY_COUNTRY_OF_ORIGIN, 7L)).thenReturn(this.codeValue);
        when(this.codeValue.isActive()).thenReturn(true);

        final CodeValue result = ReflectionTestUtils.invokeMethod(this.writeService, "findNationalityWithNotFoundDetection", 7L);

        assertThat(result).isSameAs(this.codeValue);
        verify(this.codeValueRepository).findOneByCodeNameAndIdWithNotFoundDetection(
                ClientApiConstants.NATIONALITY_COUNTRY_OF_ORIGIN, 7L);
    }

    @Test
    void findNationalityReturnsClearValidationErrorWhenValueIsNotInCountryCode() {
        when(this.codeValueRepository.findOneByCodeNameAndIdWithNotFoundDetection(
                ClientApiConstants.NATIONALITY_COUNTRY_OF_ORIGIN, 99L))
                .thenThrow(new CodeValueNotFoundException(ClientApiConstants.NATIONALITY_COUNTRY_OF_ORIGIN, 99L));

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(this.writeService, "findNationalityWithNotFoundDetection", 99L))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .satisfies(ex -> {
                    final PlatformApiDataValidationException validationException = (PlatformApiDataValidationException) ex;
                    assertThat(validationException.getErrors().get(0).getUserMessageGlobalisationCode())
                            .isEqualTo("validation.msg.client.other.info.nationality.invalid");
                    assertThat(validationException.getErrors().get(0).getDefaultUserMessage())
                            .isEqualTo("Please select a valid country of origin / nationality.");
                    assertThat(validationException.getErrors().get(0).getParameterName()).isEqualTo("nationalityId");
                });
    }

    @Test
    void findNationalityReturnsClearValidationErrorWhenCountryValueIsInactive() {
        when(this.codeValueRepository.findOneByCodeNameAndIdWithNotFoundDetection(
                ClientApiConstants.NATIONALITY_COUNTRY_OF_ORIGIN, 70L)).thenReturn(this.codeValue);
        when(this.codeValue.isActive()).thenReturn(false);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(this.writeService, "findNationalityWithNotFoundDetection", 70L))
                .isInstanceOf(PlatformApiDataValidationException.class)
                .satisfies(ex -> {
                    final PlatformApiDataValidationException validationException = (PlatformApiDataValidationException) ex;
                    assertThat(validationException.getErrors().get(0).getUserMessageGlobalisationCode())
                            .isEqualTo("validation.msg.client.other.info.nationality.invalid");
                    assertThat(validationException.getErrors().get(0).getParameterName()).isEqualTo("nationalityId");
                });
    }
}

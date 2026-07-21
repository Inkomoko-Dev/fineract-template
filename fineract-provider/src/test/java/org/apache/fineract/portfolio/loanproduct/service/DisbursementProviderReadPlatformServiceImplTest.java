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
package org.apache.fineract.portfolio.loanproduct.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.portfolio.loanproduct.domain.DisbursementProvider;
import org.apache.fineract.portfolio.loanproduct.domain.DisbursementProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DisbursementProviderReadPlatformServiceImplTest {

    @Mock
    private DisbursementProviderRepository disbursementProviderRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private DisbursementProviderReadPlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        this.underTest = new DisbursementProviderReadPlatformServiceImpl(this.disbursementProviderRepository, this.jdbcTemplate);
    }

    @Test
    void isActiveProviderTrueWhenRegistryHasActiveCode() {
        when(this.disbursementProviderRepository.findActiveByCode("KIFIYA"))
                .thenReturn(Optional.of(new DisbursementProvider("KIFIYA", "Kifiya", null, true)));

        assertTrue(this.underTest.isActiveProvider(" kifiya "));
    }

    @Test
    void isActiveProviderFalseWhenMissing() {
        when(this.disbursementProviderRepository.findActiveByCode("UNKNOWN")).thenReturn(Optional.empty());

        assertFalse(this.underTest.isActiveProvider("UNKNOWN"));
    }

    @Test
    void findActiveMappedProviderCodeUsesJdbcAndNormalizes() {
        when(this.jdbcTemplate.queryForList(anyString(), eq(String.class), eq(5L))).thenReturn(List.of("kifiya"));

        assertEquals(Optional.of("KIFIYA"), this.underTest.findActiveMappedProviderCode(5L));
        assertTrue(this.underTest.hasActiveThirdPartyDisbursementMapping(5L));
    }

    @Test
    void findActiveMappedProviderCodeEmptyWhenNoRow() {
        when(this.jdbcTemplate.queryForList(anyString(), eq(String.class), eq(5L))).thenReturn(Collections.emptyList());

        assertEquals(Optional.empty(), this.underTest.findActiveMappedProviderCode(5L));
        assertFalse(this.underTest.hasActiveThirdPartyDisbursementMapping(5L));
    }

    @Test
    void findActiveMappedProviderCodeEmptyForNullProductId() {
        assertEquals(Optional.empty(), this.underTest.findActiveMappedProviderCode(null));
    }
}

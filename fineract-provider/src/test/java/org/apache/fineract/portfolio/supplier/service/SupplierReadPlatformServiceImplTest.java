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
package org.apache.fineract.portfolio.supplier.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.supplier.data.SupplierData;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.apache.fineract.portfolio.supplier.domain.SupplierStatus;
import org.apache.fineract.portfolio.supplier.domain.SupplierSyncStatus;
import org.apache.fineract.portfolio.supplier.exception.SupplierNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SupplierReadPlatformServiceImplTest {

    @Mock
    private SupplierRepository supplierRepository;

    private SupplierReadPlatformServiceImpl readService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        this.readService = new SupplierReadPlatformServiceImpl(this.supplierRepository);
    }

    @Test
    void retrieveOneReturnsSupplierData() {
        final Supplier supplier = sampleSupplier("KIFIYA", "SUP-001", "Abebe");
        ReflectionTestUtils.setField(supplier, "id", 5L);
        when(this.supplierRepository.findById(5L)).thenReturn(java.util.Optional.of(supplier));

        final SupplierData data = this.readService.retrieveOne(5L);

        assertThat(data.getId()).isEqualTo(5L);
        assertThat(data.getExternalId()).isEqualTo("SUP-001");
        assertThat(data.getSyncStatus()).isEqualTo(SupplierSyncStatus.SUCCESS);
    }

    @Test
    void retrieveOneThrowsWhenMissing() {
        when(this.supplierRepository.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> this.readService.retrieveOne(99L)).isInstanceOf(SupplierNotFoundException.class);
    }

    @Test
    void retrieveAllReturnsMappedList() {
        final Supplier supplier = sampleSupplier("KIFIYA", "SUP-002", "Bekele");
        when(this.supplierRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(Collections.singletonList(supplier));

        final List<SupplierData> results = this.readService.retrieveAll(null, null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Bekele");
    }

    @Test
    void retrieveAllPagedReturnsPageItems() {
        final Supplier first = sampleSupplier("KIFIYA", "SUP-010", "One");
        final Supplier second = sampleSupplier("KIFIYA", "SUP-011", "Two");
        when(this.supplierRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Arrays.asList(first, second), Pageable.ofSize(15), 20));

        final Page<SupplierData> page = this.readService.retrieveAllPaged(null, "FMCG", null, null, null, 0, 15);

        assertThat(page.getPageItems()).hasSize(2);
        assertThat(page.getTotalFilteredRecords()).isEqualTo(20);
        verify(this.supplierRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void retrieveTemplateReturnsDistinctValues() {
        when(this.supplierRepository.findDistinctBusinessSectors()).thenReturn(Arrays.asList("FMCG", "Agro"));
        when(this.supplierRepository.findDistinctSupplierTypes()).thenReturn(Collections.singletonList("Exclusive"));
        when(this.supplierRepository.findDistinctCountries()).thenReturn(Collections.singletonList("Ethiopia"));

        assertThat(this.readService.retrieveTemplate().getBusinessSectorOptions()).containsExactly("FMCG", "Agro");
        assertThat(this.readService.retrieveTemplate().getCountryOptions()).containsExactly("Ethiopia");
    }

    private static Supplier sampleSupplier(final String sourceSystem, final String externalId, final String name) {
        return Supplier.create(sourceSystem, externalId, name, null, null, null, null, null, null, null, SupplierStatus.ACTIVE);
    }
}

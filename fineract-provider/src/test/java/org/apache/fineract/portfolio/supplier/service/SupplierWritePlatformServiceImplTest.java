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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.supplier.data.SupplierDataValidator;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.apache.fineract.portfolio.supplier.domain.SupplierStatus;
import org.apache.fineract.portfolio.supplier.domain.SupplierSyncStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SupplierWritePlatformServiceImplTest {

    @Mock
    private SupplierRepository supplierRepository;

    private SupplierWritePlatformServiceImpl writeService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        final SupplierDataValidator validator = new SupplierDataValidator(new FromJsonHelper());
        this.writeService = new SupplierWritePlatformServiceImpl(this.supplierRepository, validator);
    }

    @Test
    void createsNewSupplierWhenNotFound() {
        final String json = "{\"sourceSystem\":\"kifiya\",\"externalId\":\"SUP-001\",\"name\":\"Abebe\",\"displayName\":\"Abebe Co\",\"status\":\"ACTIVE\"}";
        when(this.supplierRepository.findBySourceSystemAndExternalId("KIFIYA", "SUP-001")).thenReturn(Optional.empty());
        when(this.supplierRepository.saveAndFlush(any(Supplier.class))).thenAnswer(invocation -> {
            final Supplier s = invocation.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 42L);
            return s;
        });

        final CommandProcessingResult result = this.writeService.upsert(command(json));

        assertEquals(42L, result.resourceId());
        final ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(this.supplierRepository).saveAndFlush(captor.capture());
        final Supplier saved = captor.getValue();
        assertEquals("KIFIYA", saved.getSourceSystem());
        assertEquals("SUP-001", saved.getExternalId());
        assertEquals("Abebe", saved.getName());
        assertEquals("Abebe Co", saved.getDisplayName());
        assertEquals(SupplierStatus.ACTIVE, saved.getStatus());
        assertEquals(SupplierSyncStatus.SUCCESS, saved.getSyncStatus());
        assertNull(saved.getLastSyncError());
        assertEquals(json, saved.getRawPayload());
        assertNotNull(saved.getCreatedDate());
        assertNotNull(saved.getLastModifiedDate());
    }

    @Test
    void updatesExistingSupplier() {
        final Supplier existing = Supplier.create("KIFIYA", "SUP-001", "Old Name", null, null, null, null, null, null, null,
                SupplierStatus.ACTIVE);
        ReflectionTestUtils.setField(existing, "id", 7L);

        when(this.supplierRepository.findBySourceSystemAndExternalId("KIFIYA", "SUP-001")).thenReturn(Optional.of(existing));
        when(this.supplierRepository.saveAndFlush(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final String json = "{\"sourceSystem\":\"KIFIYA\",\"externalId\":\"SUP-001\",\"name\":\"New Name\",\"displayName\":\"New Display\"}";
        final CommandProcessingResult result = this.writeService.upsert(command(json));

        assertEquals(7L, result.resourceId());
        verify(this.supplierRepository).findBySourceSystemAndExternalId("KIFIYA", "SUP-001");
        verify(this.supplierRepository).saveAndFlush(existing);
        assertEquals("New Name", existing.getName());
        assertEquals("New Display", existing.getDisplayName());
        assertEquals(SupplierSyncStatus.SUCCESS, existing.getSyncStatus());
        assertEquals(json, existing.getRawPayload());
        verify(this.supplierRepository, never()).delete(any());
    }

    private static JsonCommand command(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}

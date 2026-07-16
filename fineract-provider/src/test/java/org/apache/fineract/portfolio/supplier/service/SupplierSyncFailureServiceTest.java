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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
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
class SupplierSyncFailureServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    private SupplierSyncFailureService syncFailureService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        this.syncFailureService = new SupplierSyncFailureService(this.supplierRepository);
    }

    @Test
    void marksExistingRowFailed() {
        final Supplier existing = Supplier.create("KIFIYA", "SUP-001", "Abebe", null, null, null, null, null, null, null,
                SupplierStatus.ACTIVE);
        ReflectionTestUtils.setField(existing, "id", 9L);
        when(this.supplierRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(this.supplierRepository.saveAndFlush(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        this.syncFailureService.markFailed(9L, "boom");

        final ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(this.supplierRepository).saveAndFlush(captor.capture());
        assertEquals(SupplierSyncStatus.FAILED, captor.getValue().getSyncStatus());
        assertEquals("boom", captor.getValue().getLastSyncError());
    }

    @Test
    void ignoresNullSupplierId() {
        this.syncFailureService.markFailed(null, "boom");
        verify(this.supplierRepository, never()).findById(any());
        verify(this.supplierRepository, never()).saveAndFlush(any());
    }
}

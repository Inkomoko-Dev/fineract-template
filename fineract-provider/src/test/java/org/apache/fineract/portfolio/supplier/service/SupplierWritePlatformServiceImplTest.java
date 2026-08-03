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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.supplier.data.SupplierApiConstants;
import org.apache.fineract.portfolio.supplier.data.SupplierDataValidator;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.apache.fineract.portfolio.supplier.domain.SupplierStatus;
import org.apache.fineract.portfolio.supplier.domain.SupplierSyncStatus;
import org.apache.fineract.portfolio.loanaccount.service.SupplierPaymentDetailsValidator;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SupplierWritePlatformServiceImplTest {

    private static final String VALID_JSON = """
            {
              "sourceSystem":"kifiya",
              "externalId":"SUP-001",
              "name":"Abebe Kebede Trading PLC",
              "displayName":"Abebe Kebede",
              "businessLicenseNumber":"BL-998877",
              "supplierType":"Exclusive",
              "businessSector":"FMCG",
              "category":"TECHNOLOGY_AND_ELECTRONICS",
              "country":"Ethiopia",
              "tin":"1234567891",
              "status":"ACTIVE"
            }
            """;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierSyncFailureService syncFailureService;

    @Mock
    private PaymentTypeRepositoryWrapper paymentTypeRepositoryWrapper;

    private SupplierWritePlatformServiceImpl writeService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        final SupplierDataValidator validator = new SupplierDataValidator(new FromJsonHelper());
        this.writeService = new SupplierWritePlatformServiceImpl(this.supplierRepository, validator, this.syncFailureService,
                this.paymentTypeRepositoryWrapper, new SupplierPaymentDetailsValidator());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    @Test
    void createsNewSupplierWhenNotFound() {
        when(this.supplierRepository.findBySourceSystemAndExternalId("KIFIYA", "SUP-001")).thenReturn(Optional.empty());
        when(this.supplierRepository.findBySourceSystemAndBusinessLicenseNumberAndIdNot("KIFIYA", "BL-998877", -1L))
                .thenReturn(Optional.empty());
        when(this.supplierRepository.findBySourceSystemAndTinAndIdNot("KIFIYA", "1234567891", -1L)).thenReturn(Optional.empty());
        when(this.supplierRepository.saveAndFlush(any(Supplier.class))).thenAnswer(invocation -> {
            final Supplier s = invocation.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 42L);
            return s;
        });

        final CommandProcessingResult result = this.writeService.upsert(command(VALID_JSON));

        assertEquals(42L, result.resourceId());
        final Map<String, Object> changes = result.getChanges();
        assertNotNull(changes);
        assertEquals("SUP-001", changes.get(SupplierApiConstants.EXTERNAL_ID));
        assertEquals("KIFIYA", changes.get(SupplierApiConstants.SOURCE_SYSTEM));
        assertEquals(SupplierSyncStatus.SUCCESS.name(), changes.get(SupplierApiConstants.SYNC_STATUS));
        assertEquals(Boolean.TRUE, changes.get(SupplierApiConstants.CREATED));

        final ArgumentCaptor<Supplier> captor = ArgumentCaptor.forClass(Supplier.class);
        verify(this.supplierRepository).saveAndFlush(captor.capture());
        final Supplier saved = captor.getValue();
        assertEquals("KIFIYA", saved.getSourceSystem());
        assertEquals("SUP-001", saved.getExternalId());
        assertEquals("Abebe Kebede Trading PLC", saved.getName());
        assertEquals("Abebe Kebede", saved.getDisplayName());
        assertEquals("BL-998877", saved.getBusinessLicenseNumber());
        assertEquals("1234567891", saved.getTin());
        assertEquals(SupplierStatus.ACTIVE, saved.getStatus());
        assertEquals(SupplierSyncStatus.SUCCESS, saved.getSyncStatus());
        assertNull(saved.getLastSyncError());
        assertEquals(VALID_JSON, saved.getRawPayload());
        assertNotNull(saved.getCreatedDate());
        assertNotNull(saved.getLastModifiedDate());
        verify(this.syncFailureService, never()).markFailed(any(), any());
    }

    @Test
    void updatesExistingSupplier() {
        final Supplier existing = Supplier.create("KIFIYA", "SUP-001", "Old Name", "Old Display", "BL-OLD", "Exclusive", "FMCG", "Cat",
                "Ethiopia", "1111111111", SupplierStatus.ACTIVE);
        ReflectionTestUtils.setField(existing, "id", 7L);

        when(this.supplierRepository.findBySourceSystemAndExternalId("KIFIYA", "SUP-001")).thenReturn(Optional.of(existing));
        when(this.supplierRepository.findBySourceSystemAndBusinessLicenseNumberAndIdNot("KIFIYA", "BL-998877", 7L))
                .thenReturn(Optional.empty());
        when(this.supplierRepository.findBySourceSystemAndTinAndIdNot("KIFIYA", "1234567891", 7L)).thenReturn(Optional.empty());
        when(this.supplierRepository.saveAndFlush(any(Supplier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final CommandProcessingResult result = this.writeService.upsert(command(VALID_JSON));

        assertEquals(7L, result.resourceId());
        assertEquals("SUP-001", result.getChanges().get(SupplierApiConstants.EXTERNAL_ID));
        assertEquals("KIFIYA", result.getChanges().get(SupplierApiConstants.SOURCE_SYSTEM));
        assertEquals(SupplierSyncStatus.SUCCESS.name(), result.getChanges().get(SupplierApiConstants.SYNC_STATUS));
        assertEquals(Boolean.FALSE, result.getChanges().get(SupplierApiConstants.CREATED));
        verify(this.supplierRepository).findBySourceSystemAndExternalId("KIFIYA", "SUP-001");
        verify(this.supplierRepository).saveAndFlush(existing);
        assertEquals("Abebe Kebede Trading PLC", existing.getName());
        assertEquals("Abebe Kebede", existing.getDisplayName());
        assertEquals("BL-998877", existing.getBusinessLicenseNumber());
        assertEquals(SupplierSyncStatus.SUCCESS, existing.getSyncStatus());
        verify(this.syncFailureService, never()).markFailed(any(), any());
    }

    @Test
    void rejectsDuplicateBusinessLicenseNumber() {
        when(this.supplierRepository.findBySourceSystemAndExternalId("KIFIYA", "SUP-001")).thenReturn(Optional.empty());
        final Supplier conflict = Supplier.create("KIFIYA", "SUP-OTHER", "Other", "Other", "BL-998877", "Exclusive", "FMCG", "Cat",
                "Ethiopia", "9999999999", SupplierStatus.ACTIVE);
        ReflectionTestUtils.setField(conflict, "id", 99L);
        when(this.supplierRepository.findBySourceSystemAndBusinessLicenseNumberAndIdNot("KIFIYA", "BL-998877", -1L))
                .thenReturn(Optional.of(conflict));

        assertThatThrownBy(() -> this.writeService.upsert(command(VALID_JSON))).isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getErrors().get(0).getUserMessageGlobalisationCode())
                .isEqualTo("validation.msg.supplier.businessLicenseNumber.duplicate");
        verify(this.supplierRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateTin() {
        when(this.supplierRepository.findBySourceSystemAndExternalId("KIFIYA", "SUP-001")).thenReturn(Optional.empty());
        when(this.supplierRepository.findBySourceSystemAndBusinessLicenseNumberAndIdNot("KIFIYA", "BL-998877", -1L))
                .thenReturn(Optional.empty());
        final Supplier conflict = Supplier.create("KIFIYA", "SUP-OTHER", "Other", "Other", "BL-OTHER", "Exclusive", "FMCG", "Cat",
                "Ethiopia", "1234567891", SupplierStatus.ACTIVE);
        ReflectionTestUtils.setField(conflict, "id", 99L);
        when(this.supplierRepository.findBySourceSystemAndTinAndIdNot("KIFIYA", "1234567891", -1L)).thenReturn(Optional.of(conflict));

        assertThatThrownBy(() -> this.writeService.upsert(command(VALID_JSON))).isInstanceOf(PlatformApiDataValidationException.class)
                .extracting(ex -> ((PlatformApiDataValidationException) ex).getErrors().get(0).getUserMessageGlobalisationCode())
                .isEqualTo("validation.msg.supplier.tin.duplicate");
        verify(this.supplierRepository, never()).saveAndFlush(any());
    }

    @Test
    void marksExistingSupplierFailedWhenSaveThrows() {
        final Supplier existing = Supplier.create("KIFIYA", "SUP-001", "Abebe", "Abebe", "BL-1", "Exclusive", "FMCG", "Cat", "Ethiopia",
                "123", SupplierStatus.ACTIVE);
        ReflectionTestUtils.setField(existing, "id", 7L);

        when(this.supplierRepository.findBySourceSystemAndExternalId("KIFIYA", "SUP-001")).thenReturn(Optional.of(existing));
        when(this.supplierRepository.findBySourceSystemAndBusinessLicenseNumberAndIdNot("KIFIYA", "BL-998877", 7L))
                .thenReturn(Optional.empty());
        when(this.supplierRepository.findBySourceSystemAndTinAndIdNot("KIFIYA", "1234567891", 7L)).thenReturn(Optional.empty());
        when(this.supplierRepository.saveAndFlush(any(Supplier.class))).thenThrow(new RuntimeException("db unavailable"));

        final RuntimeException thrown = assertThrows(RuntimeException.class, () -> this.writeService.upsert(command(VALID_JSON)));

        assertEquals("db unavailable", thrown.getMessage());
        verify(this.syncFailureService).markFailed(eq(7L), eq("db unavailable"));
    }

    @Test
    void doesNotMarkFailedWhenNewSupplierHasNoIdYet() {
        when(this.supplierRepository.findBySourceSystemAndExternalId("KIFIYA", "SUP-001")).thenReturn(Optional.empty());
        when(this.supplierRepository.findBySourceSystemAndBusinessLicenseNumberAndIdNot("KIFIYA", "BL-998877", -1L))
                .thenReturn(Optional.empty());
        when(this.supplierRepository.findBySourceSystemAndTinAndIdNot("KIFIYA", "1234567891", -1L)).thenReturn(Optional.empty());
        when(this.supplierRepository.saveAndFlush(any(Supplier.class))).thenThrow(new RuntimeException("unique violation"));

        assertThrows(RuntimeException.class, () -> this.writeService.upsert(command(VALID_JSON)));

        verify(this.syncFailureService, never()).markFailed(any(), any());
    }

    @Test
    void allowsSameRecordLicenseAndTinOnUpdate() {
        when(this.supplierRepository.findBySourceSystemAndBusinessLicenseNumberAndIdNot("KIFIYA", "BL-998877", 7L))
                .thenReturn(Optional.empty());
        when(this.supplierRepository.findBySourceSystemAndTinAndIdNot("KIFIYA", "1234567891", 7L)).thenReturn(Optional.empty());

        assertThatCode(() -> this.writeService.assertUniqueBusinessLicenseAndTin("KIFIYA", "BL-998877", "1234567891", 7L))
                .doesNotThrowAnyException();
    }

    private static JsonCommand command(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), null, null, null, null, null, null, null, null,
                null, null, null, null);
    }
}

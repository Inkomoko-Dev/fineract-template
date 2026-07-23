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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.supplier.data.SupplierApiConstants;
import org.apache.fineract.portfolio.supplier.data.SupplierDataValidator;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.apache.fineract.portfolio.supplier.domain.SupplierStatus;
import org.apache.fineract.portfolio.supplier.domain.SupplierSyncStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SupplierWritePlatformServiceImpl implements SupplierWritePlatformService {

    private static final Logger LOG = LoggerFactory.getLogger(SupplierWritePlatformServiceImpl.class);

    private final SupplierRepository supplierRepository;
    private final SupplierDataValidator validator;
    private final SupplierSyncFailureService syncFailureService;

    @Autowired
    public SupplierWritePlatformServiceImpl(final SupplierRepository supplierRepository, final SupplierDataValidator validator,
            final SupplierSyncFailureService syncFailureService) {
        this.supplierRepository = supplierRepository;
        this.validator = validator;
        this.syncFailureService = syncFailureService;
    }

    @Override
    public CommandProcessingResult upsert(final JsonCommand command) {
        this.validator.validateForUpsert(command.json());

        final String sourceSystem = normalizeSourceSystem(command.stringValueOfParameterNamed(SupplierApiConstants.SOURCE_SYSTEM));
        final String externalId = trimRequired(command.stringValueOfParameterNamed(SupplierApiConstants.EXTERNAL_ID));
        final String name = command.stringValueOfParameterNamed(SupplierApiConstants.NAME);
        final String displayName = command.stringValueOfParameterNamed(SupplierApiConstants.DISPLAY_NAME);
        final String businessLicenseNumber = trimRequired(
                command.stringValueOfParameterNamed(SupplierApiConstants.BUSINESS_LICENSE_NUMBER));
        final String supplierType = command.stringValueOfParameterNamed(SupplierApiConstants.SUPPLIER_TYPE);
        final String businessSector = command.stringValueOfParameterNamed(SupplierApiConstants.BUSINESS_SECTOR);
        final String category = command.stringValueOfParameterNamed(SupplierApiConstants.CATEGORY);
        final String country = command.stringValueOfParameterNamed(SupplierApiConstants.COUNTRY);
        final String tin = trimRequired(command.stringValueOfParameterNamed(SupplierApiConstants.TIN));
        final SupplierStatus status = SupplierStatus.from(command.stringValueOfParameterNamedAllowingNull(SupplierApiConstants.STATUS));

        Supplier supplier = null;
        final boolean created;
        try {
            final Optional<Supplier> existing = this.supplierRepository.findBySourceSystemAndExternalId(sourceSystem, externalId);
            created = existing.isEmpty();
            if (created) {
                supplier = Supplier.create(sourceSystem, externalId, name, displayName, businessLicenseNumber, supplierType, businessSector,
                        category, country, tin, status);
            } else {
                supplier = existing.get();
                supplier.updateFrom(name, displayName, businessLicenseNumber, supplierType, businessSector, category, country, tin, status);
            }
            assertUniqueBusinessLicenseAndTin(sourceSystem, businessLicenseNumber, tin, supplier.getId());
            supplier.setRawPayload(command.json());
            this.supplierRepository.saveAndFlush(supplier);
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(supplier.getId())
                    .with(buildCallbackChanges(supplier, created)).build();
        } catch (final RuntimeException ex) {
            LOG.error("Supplier upsert failed for sourceSystem={} externalId={}: {}", sourceSystem, externalId, ex.getMessage(), ex);
            if (supplier != null && supplier.getId() != null) {
                try {
                    this.syncFailureService.markFailed(supplier.getId(), ex.getMessage());
                } catch (final RuntimeException markFailedEx) {
                    LOG.warn("Could not persist FAILED sync status for supplier {}: {}", supplier.getId(), markFailedEx.getMessage(),
                            markFailedEx);
                }
            }
            throw ex;
        }
    }

    void assertUniqueBusinessLicenseAndTin(final String sourceSystem, final String businessLicenseNumber, final String tin,
            final Long supplierId) {
        final Long excludeId = supplierId == null ? -1L : supplierId;
        final List<ApiParameterError> errors = new java.util.ArrayList<>();

        this.supplierRepository.findBySourceSystemAndBusinessLicenseNumberAndIdNot(sourceSystem, businessLicenseNumber, excludeId)
                .ifPresent(conflict -> errors.add(ApiParameterError.parameterError(
                        "validation.msg.supplier.businessLicenseNumber.duplicate",
                        "Business license number is already registered for this source system.",
                        SupplierApiConstants.BUSINESS_LICENSE_NUMBER, businessLicenseNumber)));

        this.supplierRepository.findBySourceSystemAndTinAndIdNot(sourceSystem, tin, excludeId)
                .ifPresent(conflict -> errors.add(ApiParameterError.parameterError("validation.msg.supplier.tin.duplicate",
                        "TIN is already registered for this source system.", SupplierApiConstants.TIN, tin)));

        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }

    private static Map<String, Object> buildCallbackChanges(final Supplier supplier, final boolean created) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(SupplierApiConstants.EXTERNAL_ID, supplier.getExternalId());
        changes.put(SupplierApiConstants.SOURCE_SYSTEM, supplier.getSourceSystem());
        changes.put(SupplierApiConstants.SYNC_STATUS, SupplierSyncStatus.SUCCESS.name());
        changes.put(SupplierApiConstants.CREATED, created);
        return changes;
    }

    private static String normalizeSourceSystem(final String sourceSystem) {
        return sourceSystem == null ? null : sourceSystem.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimRequired(final String value) {
        return value == null ? null : value.trim();
    }
}

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

import java.util.Locale;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.portfolio.supplier.data.SupplierApiConstants;
import org.apache.fineract.portfolio.supplier.data.SupplierDataValidator;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.apache.fineract.portfolio.supplier.domain.SupplierStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SupplierWritePlatformServiceImpl implements SupplierWritePlatformService {

    private final SupplierRepository supplierRepository;
    private final SupplierDataValidator validator;

    @Autowired
    public SupplierWritePlatformServiceImpl(final SupplierRepository supplierRepository, final SupplierDataValidator validator) {
        this.supplierRepository = supplierRepository;
        this.validator = validator;
    }

    @Override
    public CommandProcessingResult upsert(final JsonCommand command) {
        this.validator.validateForUpsert(command.json());

        final String sourceSystem = normalizeSourceSystem(
                command.stringValueOfParameterNamed(SupplierApiConstants.SOURCE_SYSTEM));
        final String externalId = trimRequired(command.stringValueOfParameterNamed(SupplierApiConstants.EXTERNAL_ID));
        final String name = command.stringValueOfParameterNamed(SupplierApiConstants.NAME);
        final String displayName = command.stringValueOfParameterNamedAllowingNull(SupplierApiConstants.DISPLAY_NAME);
        final String businessLicenseNumber = command
                .stringValueOfParameterNamedAllowingNull(SupplierApiConstants.BUSINESS_LICENSE_NUMBER);
        final String supplierType = command.stringValueOfParameterNamedAllowingNull(SupplierApiConstants.SUPPLIER_TYPE);
        final String businessSector = command.stringValueOfParameterNamedAllowingNull(SupplierApiConstants.BUSINESS_SECTOR);
        final String category = command.stringValueOfParameterNamedAllowingNull(SupplierApiConstants.CATEGORY);
        final String country = command.stringValueOfParameterNamedAllowingNull(SupplierApiConstants.COUNTRY);
        final String tin = command.stringValueOfParameterNamedAllowingNull(SupplierApiConstants.TIN);
        final SupplierStatus status = SupplierStatus
                .from(command.stringValueOfParameterNamedAllowingNull(SupplierApiConstants.STATUS));

        Supplier supplier = null;
        try {
            final Optional<Supplier> existing = this.supplierRepository.findBySourceSystemAndExternalId(sourceSystem, externalId);
            if (existing.isEmpty()) {
                supplier = Supplier.create(sourceSystem, externalId, name, displayName, businessLicenseNumber, supplierType,
                        businessSector, category, country, tin, status);
            } else {
                supplier = existing.get();
                supplier.updateFrom(name, displayName, businessLicenseNumber, supplierType, businessSector, category, country, tin,
                        status);
            }
            supplier.setRawPayload(command.json());
            this.supplierRepository.saveAndFlush(supplier);
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(supplier.getId()).build();
        } catch (final RuntimeException ex) {
            if (supplier != null && supplier.getId() != null) {
                try {
                    supplier.markFailed(ex.getMessage());
                    this.supplierRepository.saveAndFlush(supplier);
                } catch (final RuntimeException ignored) {
                    // preserve original failure
                }
            }
            throw ex;
        }
    }

    private static String normalizeSourceSystem(final String sourceSystem) {
        return sourceSystem == null ? null : sourceSystem.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimRequired(final String value) {
        return value == null ? null : value.trim();
    }
}

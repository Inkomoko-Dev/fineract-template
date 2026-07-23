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
package org.apache.fineract.portfolio.supplier.domain;

import java.time.LocalDateTime;
import java.util.Locale;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;

@Entity
@Table(name = "m_supplier", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "source_system", "external_id" }, name = "uq_m_supplier_source_system_external_id"),
        @UniqueConstraint(columnNames = { "source_system", "business_license_number" },
                name = "uq_m_supplier_source_system_business_license_number"),
        @UniqueConstraint(columnNames = { "source_system", "tin" }, name = "uq_m_supplier_source_system_tin")
})
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends AbstractPersistableCustom {

    private static final int LAST_SYNC_ERROR_MAX_LENGTH = 1000;

    @Column(name = "source_system", nullable = false, length = 50)
    private String sourceSystem;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "business_license_number", length = 100)
    private String businessLicenseNumber;

    @Column(name = "supplier_type", length = 100)
    private String supplierType;

    @Column(name = "business_sector", length = 100)
    private String businessSector;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "tin", length = 50)
    private String tin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupplierStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private SupplierSyncStatus syncStatus;

    @Column(name = "last_sync_error", length = 1000)
    private String lastSyncError;

    @Lob
    @Column(name = "raw_payload")
    private String rawPayload;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "last_modified_date")
    private LocalDateTime lastModifiedDate;

    public static Supplier create(final String sourceSystem, final String externalId, final String name, final String displayName,
            final String businessLicenseNumber, final String supplierType, final String businessSector, final String category,
            final String country, final String tin, final SupplierStatus status) {
        final Supplier supplier = new Supplier();
        supplier.sourceSystem = normalizeSourceSystem(sourceSystem);
        supplier.externalId = trimRequired(externalId);
        supplier.applyProfileFields(name, displayName, businessLicenseNumber, supplierType, businessSector, category, country, tin,
                status);
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        supplier.createdDate = now;
        supplier.lastModifiedDate = now;
        return supplier;
    }

    public void updateFrom(final String name, final String displayName, final String businessLicenseNumber, final String supplierType,
            final String businessSector, final String category, final String country, final String tin, final SupplierStatus status) {
        applyProfileFields(name, displayName, businessLicenseNumber, supplierType, businessSector, category, country, tin, status);
        this.lastModifiedDate = DateUtils.getLocalDateTimeOfTenant();
    }

    public void markFailed(final String error) {
        this.syncStatus = SupplierSyncStatus.FAILED;
        this.lastSyncError = truncateError(error);
        this.lastModifiedDate = DateUtils.getLocalDateTimeOfTenant();
    }

    private void applyProfileFields(final String name, final String displayName, final String businessLicenseNumber,
            final String supplierType, final String businessSector, final String category, final String country, final String tin,
            final SupplierStatus status) {
        this.name = trimRequired(name);
        this.displayName = trimOptional(displayName);
        this.businessLicenseNumber = trimOptional(businessLicenseNumber);
        this.supplierType = trimOptional(supplierType);
        this.businessSector = trimOptional(businessSector);
        this.category = trimOptional(category);
        this.country = trimOptional(country);
        this.tin = trimOptional(tin);
        this.status = status == null ? SupplierStatus.ACTIVE : status;
        this.syncStatus = SupplierSyncStatus.SUCCESS;
        this.lastSyncError = null;
    }

    private static String normalizeSourceSystem(final String sourceSystem) {
        return sourceSystem == null ? null : sourceSystem.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimRequired(final String value) {
        return value == null ? null : value.trim();
    }

    private static String trimOptional(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncateError(final String error) {
        if (error == null) {
            return null;
        }
        if (error.length() <= LAST_SYNC_ERROR_MAX_LENGTH) {
            return error;
        }
        return error.substring(0, LAST_SYNC_ERROR_MAX_LENGTH);
    }
}

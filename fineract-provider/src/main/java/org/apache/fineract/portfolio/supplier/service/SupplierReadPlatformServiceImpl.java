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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.persistence.criteria.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.portfolio.supplier.data.SupplierData;
import org.apache.fineract.portfolio.supplier.data.SupplierTemplateData;
import org.apache.fineract.portfolio.supplier.domain.Supplier;
import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.apache.fineract.portfolio.supplier.domain.SupplierSyncStatus;
import org.apache.fineract.portfolio.supplier.exception.SupplierNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SupplierReadPlatformServiceImpl implements SupplierReadPlatformService {

    private final SupplierRepository supplierRepository;

    @Autowired
    public SupplierReadPlatformServiceImpl(final SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Override
    public SupplierData retrieveOne(final Long supplierId) {
        final Supplier supplier = this.supplierRepository.findById(supplierId)
                .orElseThrow(() -> new SupplierNotFoundException(supplierId));
        return SupplierData.from(supplier);
    }

    @Override
    public List<SupplierData> retrieveAll(final String search, final String businessSector, final String supplierType, final String country,
            final String syncStatus) {
        final Specification<Supplier> spec = buildSpecification(search, businessSector, supplierType, country, syncStatus);
        final Sort sort = Sort.by(Sort.Direction.DESC, "lastModifiedDate");
        return this.supplierRepository.findAll(spec, sort).stream().map(SupplierData::from).collect(Collectors.toList());
    }

    @Override
    public Page<SupplierData> retrieveAllPaged(final String search, final String businessSector, final String supplierType,
            final String country, final String syncStatus, final Integer offset, final Integer limit) {
        final Specification<Supplier> spec = buildSpecification(search, businessSector, supplierType, country, syncStatus);
        final Sort sort = Sort.by(Sort.Direction.DESC, "lastModifiedDate");
        final int safeOffset = offset == null || offset < 0 ? 0 : offset;
        final int safeLimit = limit == null || limit <= 0 ? 15 : limit;
        final org.springframework.data.domain.Page<Supplier> page = this.supplierRepository.findAll(spec,
                PageRequest.of(safeOffset / safeLimit, safeLimit, sort));
        final List<SupplierData> pageItems = page.getContent().stream().map(SupplierData::from).collect(Collectors.toList());
        return new Page<>(pageItems, Math.toIntExact(page.getTotalElements()));
    }

    @Override
    public SupplierTemplateData retrieveTemplate() {
        return new SupplierTemplateData(this.supplierRepository.findDistinctBusinessSectors(),
                this.supplierRepository.findDistinctSupplierTypes(), this.supplierRepository.findDistinctCountries());
    }

    private Specification<Supplier> buildSpecification(final String search, final String businessSector, final String supplierType,
            final String country, final String syncStatus) {
        return (root, query, cb) -> {
            final List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.isNotBlank(search)) {
                final String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("displayName")), like),
                        cb.like(cb.lower(root.get("externalId")), like), cb.like(cb.lower(root.get("tin")), like)));
            }
            if (StringUtils.isNotBlank(businessSector)) {
                predicates.add(cb.equal(root.get("businessSector"), businessSector.trim()));
            }
            if (StringUtils.isNotBlank(supplierType)) {
                predicates.add(cb.equal(root.get("supplierType"), supplierType.trim()));
            }
            if (StringUtils.isNotBlank(country)) {
                predicates.add(cb.equal(root.get("country"), country.trim()));
            }
            if (StringUtils.isNotBlank(syncStatus)) {
                predicates.add(cb.equal(root.get("syncStatus"), SupplierSyncStatus.valueOf(syncStatus.trim().toUpperCase(Locale.ROOT))));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

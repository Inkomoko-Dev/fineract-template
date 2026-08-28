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

import org.apache.fineract.portfolio.supplier.domain.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists supplier sync failures in a new transaction so FAILED status survives rollback of the upsert.
 */
@Service
public class SupplierSyncFailureService {

    private static final Logger LOG = LoggerFactory.getLogger(SupplierSyncFailureService.class);

    private final SupplierRepository supplierRepository;

    @Autowired
    public SupplierSyncFailureService(final SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final Long supplierId, final String error) {
        if (supplierId == null) {
            return;
        }
        this.supplierRepository.findById(supplierId).ifPresentOrElse(supplier -> {
            supplier.markFailed(error);
            this.supplierRepository.saveAndFlush(supplier);
            LOG.warn("Marked supplier {} syncStatus=FAILED: {}", supplierId, error);
        }, () -> LOG.warn("Could not mark supplier {} FAILED; row not found", supplierId));
    }
}

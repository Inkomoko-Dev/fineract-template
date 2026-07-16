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

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    Optional<Supplier> findBySourceSystemAndExternalId(String sourceSystem, String externalId);

    @Query("SELECT DISTINCT s.businessSector FROM Supplier s WHERE s.businessSector IS NOT NULL ORDER BY s.businessSector")
    List<String> findDistinctBusinessSectors();

    @Query("SELECT DISTINCT s.supplierType FROM Supplier s WHERE s.supplierType IS NOT NULL ORDER BY s.supplierType")
    List<String> findDistinctSupplierTypes();

    @Query("SELECT DISTINCT s.country FROM Supplier s WHERE s.country IS NOT NULL ORDER BY s.country")
    List<String> findDistinctCountries();
}

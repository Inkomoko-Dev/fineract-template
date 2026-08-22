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
package org.apache.fineract.portfolio.client.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerClientMappingRepository extends JpaRepository<PartnerClientMapping, Long>, JpaSpecificationExecutor<PartnerClientMapping> {

    @Query("SELECT m FROM PartnerClientMapping m WHERE m.client.id = :clientId")
    Optional<PartnerClientMapping> findByClientId(@Param("clientId") Long clientId);

    @Query("SELECT m FROM PartnerClientMapping m WHERE m.partnerCode = :partnerCode AND m.isActive = true")
    List<PartnerClientMapping> findByPartnerCodeAndIsActiveTrue(@Param("partnerCode") String partnerCode);

    @Query("SELECT m FROM PartnerClientMapping m WHERE m.client.id = :clientId AND m.partnerCode = :partnerCode AND m.isActive = true")
    Optional<PartnerClientMapping> findByClientIdAndPartnerCodeAndIsActiveTrue(@Param("clientId") Long clientId, @Param("partnerCode") String partnerCode);

    @Query("SELECT m FROM PartnerClientMapping m WHERE m.client.id = :clientId AND m.isActive = true")
    Optional<PartnerClientMapping> findByClientIdAndIsActiveTrue(@Param("clientId") Long clientId);

    @Query("SELECT m FROM PartnerClientMapping m JOIN m.client c WHERE c.mobileNo = :phoneNumber AND m.partnerCode = :partnerCode AND m.isActive = true")
    Optional<PartnerClientMapping> findByClientPhoneNumberAndPartnerCode(@Param("phoneNumber") String phoneNumber,
            @Param("partnerCode") String partnerCode);

    @Query("SELECT m FROM PartnerClientMapping m JOIN m.client c WHERE m.partnerCode = :partnerCode AND m.isActive = true " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:officeId IS NULL OR c.office.id = :officeId) " +
           "AND (:fromDate IS NULL OR m.assignedDate >= :fromDate) " +
           "AND (:toDate IS NULL OR m.assignedDate <= :toDate)")
    Page<PartnerClientMapping> findPartnerClientsWithFilters(@Param("partnerCode") String partnerCode,
            @Param("status") Integer status, @Param("officeId") Long officeId, @Param("fromDate") java.time.LocalDate fromDate,
            @Param("toDate") java.time.LocalDate toDate, Pageable pageable);
}

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
package org.apache.fineract.portfolio.account.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTransferDetailRepository
        extends JpaRepository<AccountTransferDetails, Long>, JpaSpecificationExecutor<AccountTransferDetails> {

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM AccountTransferDetails a WHERE a.fromClient.id = :clientId1 OR a.toClient.id = :clientId2")
    boolean existsByFromClientIdOrToClientId(@Param("clientId1") Long clientId1, @Param("clientId2") Long clientId2);

    @Query("SELECT a FROM AccountTransferDetails a WHERE a.fromClient.id = :clientId1 OR a.toClient.id = :clientId2 ORDER BY a.id DESC")
    AccountTransferDetails findTopByFromClientIdOrToClientIdOrderByIdDesc(@Param("clientId1") Long clientId1, @Param("clientId2") Long clientId2);
}

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
package org.apache.fineract.infrastructure.whatsapp.interactive.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsAppOptOutRecordRepository extends JpaRepository<WhatsAppOptOutRecord, Long> {

    @Query("select o from WhatsAppOptOutRecord o where o.phoneNumber = :phoneNumber order by o.createdDate desc")
    java.util.List<WhatsAppOptOutRecord> findByPhoneNumberOrderByCreatedDateDesc(@Param("phoneNumber") String phoneNumber);

    default Optional<WhatsAppOptOutRecord> findLatest(final String phoneNumber) {
        final java.util.List<WhatsAppOptOutRecord> records = findByPhoneNumberOrderByCreatedDateDesc(phoneNumber);
        return records.isEmpty() ? Optional.empty() : Optional.of(records.get(0));
    }
}

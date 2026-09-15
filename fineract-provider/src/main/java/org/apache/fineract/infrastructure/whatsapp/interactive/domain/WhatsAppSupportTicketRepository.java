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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsAppSupportTicketRepository extends JpaRepository<WhatsAppSupportTicket, Long> {

    Optional<WhatsAppSupportTicket> findByTicketNumber(String ticketNumber);

    List<WhatsAppSupportTicket> findByStatusOrderByCreatedDateDesc(WhatsAppTicketStatus status);

    List<WhatsAppSupportTicket> findByAssignedStaffIdOrderByCreatedDateDesc(Long staffId);

    List<WhatsAppSupportTicket> findAllByOrderByCreatedDateDesc();

    @Query("select t from WhatsAppSupportTicket t where t.slaDueAt < :now and t.firstResponseAt is null and t.status not in :excludedStatuses")
    List<WhatsAppSupportTicket> findSlaBreachedTickets(@Param("now") LocalDateTime now,
            @Param("excludedStatuses") List<WhatsAppTicketStatus> excludedStatuses);
}

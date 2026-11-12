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
package org.apache.fineract.infrastructure.whatsapp.interactive.data;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketPriority;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketStatus;

@Getter
@Setter
@NoArgsConstructor
public class WhatsAppSupportTicketData {

    private Long id;
    private String ticketNumber;
    private String phoneNumber;
    private Long clientId;
    private String clientDisplayName;
    private Long conversationSessionId;
    private String category;
    private WhatsAppTicketStatus status;
    private WhatsAppTicketPriority priority;
    private String summary;
    private String customerMessage;
    private String languageCode;
    private Long assignedStaffId;
    private String assignedStaffName;
    private LocalDateTime slaDueAt;
    private boolean slaBreached;
    private LocalDateTime firstResponseAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdDate;
    private LocalDateTime lastModifiedDate;
}

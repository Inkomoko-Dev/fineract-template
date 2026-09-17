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
package org.apache.fineract.infrastructure.whatsapp.interactive.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessage;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageRepository;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketPriority;
import org.apache.fineract.infrastructure.whatsapp.interactive.constants.WhatsAppTicketStatus;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppSupportTicketData;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppTicketConversationMessageData;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppConversationSession;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicket;
import org.apache.fineract.infrastructure.whatsapp.interactive.domain.WhatsAppSupportTicketRepository;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppSupportTicketService {

    private static final DateTimeFormatter TICKET_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final WhatsAppSupportTicketRepository ticketRepository;
    private final WhatsAppBusinessHoursService businessHoursService;
    private final CommunicationMessageRepository communicationMessageRepository;
    private final StaffRepositoryWrapper staffRepositoryWrapper;

    @Transactional
    public WhatsAppSupportTicket createFromEscalation(final WhatsAppConversationSession session, final Client client,
            final String category, final String customerMessage, final String languageCode) {
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        final WhatsAppSupportTicket ticket = new WhatsAppSupportTicket();
        ticket.setTicketNumber(generateTicketNumber(now));
        ticket.setPhoneNumber(session.getPhoneNumber());
        ticket.setClient(client);
        ticket.setConversationSessionId(session.getId());
        ticket.setCategory(StringUtils.defaultIfBlank(category, "GENERAL"));
        ticket.setStatus(WhatsAppTicketStatus.OPEN);
        ticket.setPriority(WhatsAppTicketPriority.NORMAL);
        ticket.setSummary(buildSummary(category, customerMessage));
        ticket.setCustomerMessage(customerMessage);
        ticket.setLanguageCode(languageCode);
        ticket.setSlaDueAt(businessHoursService.calculateSlaDueAt(now));
        ticket.setCreatedDate(now);
        ticket.setLastModifiedDate(now);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public WhatsAppSupportTicket createFromVoiceEscalation(final String phoneNumber, final Long voiceIvrSessionId, final Client client,
            final String category, final String languageCode) {
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        final WhatsAppSupportTicket ticket = new WhatsAppSupportTicket();
        ticket.setTicketNumber(generateTicketNumber(now));
        ticket.setPhoneNumber(phoneNumber);
        ticket.setClient(client);
        ticket.setVoiceIvrSessionId(voiceIvrSessionId);
        ticket.setCategory(StringUtils.defaultIfBlank(category, "VOICE"));
        ticket.setStatus(WhatsAppTicketStatus.OPEN);
        ticket.setPriority(WhatsAppTicketPriority.NORMAL);
        ticket.setSummary("Voice call escalation to " + StringUtils.defaultIfBlank(category, "support"));
        ticket.setLanguageCode(languageCode);
        ticket.setSlaDueAt(businessHoursService.calculateSlaDueAt(now));
        ticket.setCreatedDate(now);
        ticket.setLastModifiedDate(now);
        return ticketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<WhatsAppSupportTicketData> retrieveTickets(final WhatsAppTicketStatus status, final Long assignedStaffId) {
        final List<WhatsAppSupportTicket> tickets;
        if (assignedStaffId != null) {
            tickets = ticketRepository.findByAssignedStaffIdOrderByCreatedDateDesc(assignedStaffId);
        } else if (status != null) {
            tickets = ticketRepository.findByStatusOrderByCreatedDateDesc(status);
        } else {
            tickets = ticketRepository.findAllByOrderByCreatedDateDesc();
        }
        return tickets.stream().map(this::mapTicket).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WhatsAppSupportTicketData retrieveTicket(final Long ticketId) {
        return ticketRepository.findById(ticketId).map(this::mapTicket)
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp support ticket not found: " + ticketId));
    }

    @Transactional(readOnly = true)
    public List<WhatsAppTicketConversationMessageData> retrieveConversationHistory(final String phoneNumber) {
        return communicationMessageRepository
                .findTop100ByPhoneNumberAndChannelOrderByCreatedDateDesc(phoneNumber, CommunicationChannel.WHATSAPP).stream()
                .map(this::mapConversationMessage).collect(Collectors.toList());
    }

    @Transactional
    public WhatsAppSupportTicketData assignTicket(final Long ticketId, final Long staffId) {
        final WhatsAppSupportTicket ticket = loadTicket(ticketId);
        final Staff staff = staffRepositoryWrapper.findOneWithNotFoundDetection(staffId);
        ticket.setAssignedStaff(staff);
        if (ticket.getStatus() == WhatsAppTicketStatus.OPEN) {
            ticket.setStatus(WhatsAppTicketStatus.ASSIGNED);
        }
        touch(ticket);
        return mapTicket(ticketRepository.save(ticket));
    }

    @Transactional
    public WhatsAppSupportTicketData updateStatus(final Long ticketId, final WhatsAppTicketStatus status, final boolean recordFirstResponse) {
        final WhatsAppSupportTicket ticket = loadTicket(ticketId);
        final LocalDateTime now = DateUtils.getLocalDateTimeOfTenant();
        ticket.setStatus(status);
        if (recordFirstResponse && ticket.getFirstResponseAt() == null) {
            ticket.setFirstResponseAt(now);
        }
        if (status == WhatsAppTicketStatus.RESOLVED || status == WhatsAppTicketStatus.CLOSED) {
            ticket.setResolvedAt(now);
        }
        touch(ticket);
        return mapTicket(ticketRepository.save(ticket));
    }

    @Transactional
    public WhatsAppSupportTicketData updateSummary(final Long ticketId, final String summary) {
        final WhatsAppSupportTicket ticket = loadTicket(ticketId);
        ticket.setSummary(summary);
        touch(ticket);
        return mapTicket(ticketRepository.save(ticket));
    }

    private WhatsAppSupportTicket loadTicket(final Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp support ticket not found: " + ticketId));
    }

    private void touch(final WhatsAppSupportTicket ticket) {
        ticket.setLastModifiedDate(DateUtils.getLocalDateTimeOfTenant());
    }

    private String generateTicketNumber(final LocalDateTime createdAt) {
        final String datePart = createdAt.format(TICKET_DATE_FORMAT);
        return "WA-" + datePart + "-" + String.format("%04d", ticketRepository.count() + 1);
    }

    private String buildSummary(final String category, final String customerMessage) {
        if (StringUtils.isNotBlank(customerMessage)) {
            final String trimmed = customerMessage.trim();
            return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
        }
        return "WhatsApp " + StringUtils.defaultIfBlank(category, "GENERAL") + " enquiry";
    }

    private WhatsAppSupportTicketData mapTicket(final WhatsAppSupportTicket ticket) {
        final WhatsAppSupportTicketData data = new WhatsAppSupportTicketData();
        data.setId(ticket.getId());
        data.setTicketNumber(ticket.getTicketNumber());
        data.setPhoneNumber(ticket.getPhoneNumber());
        if (ticket.getClient() != null) {
            data.setClientId(ticket.getClient().getId());
            data.setClientDisplayName(ticket.getClient().getDisplayName());
        }
        data.setConversationSessionId(ticket.getConversationSessionId());
        data.setCategory(ticket.getCategory());
        data.setStatus(ticket.getStatus());
        data.setPriority(ticket.getPriority());
        data.setSummary(ticket.getSummary());
        data.setCustomerMessage(ticket.getCustomerMessage());
        data.setLanguageCode(ticket.getLanguageCode());
        if (ticket.getAssignedStaff() != null) {
            data.setAssignedStaffId(ticket.getAssignedStaff().getId());
            data.setAssignedStaffName(ticket.getAssignedStaff().displayName());
        }
        data.setSlaDueAt(ticket.getSlaDueAt());
        data.setSlaBreached(ticket.getSlaDueAt() != null && ticket.getFirstResponseAt() == null
                && DateUtils.getLocalDateTimeOfTenant().isAfter(ticket.getSlaDueAt()));
        data.setFirstResponseAt(ticket.getFirstResponseAt());
        data.setResolvedAt(ticket.getResolvedAt());
        data.setCreatedDate(ticket.getCreatedDate());
        data.setLastModifiedDate(ticket.getLastModifiedDate());
        return data;
    }

    private WhatsAppTicketConversationMessageData mapConversationMessage(final CommunicationMessage message) {
        final WhatsAppTicketConversationMessageData data = new WhatsAppTicketConversationMessageData();
        data.setId(message.getId());
        data.setDirection(message.getDirection());
        data.setMessageBody(message.getMessageBody());
        data.setCreatedDate(message.getCreatedDate());
        return data;
    }
}

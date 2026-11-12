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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.whatsapp.interactive.data.WhatsAppConversationSearchResultData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WhatsAppConversationSearchService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<WhatsAppConversationSearchResultData> search(final String phoneNumber, final Long clientId, final String text,
            final LocalDate fromDate, final LocalDate toDate, final String recipientType, final Integer limit) {
        final StringBuilder sql = new StringBuilder(
                "select cm.id, cm.phone_number, cm.client_id, c.display_name, cm.direction, cm.recipient_type, cm.message_body, cm.created_date "
                        + "from communication_message cm left join m_client c on c.id = cm.client_id where cm.channel = ?");
        final List<Object> params = new ArrayList<>();
        params.add(CommunicationChannel.WHATSAPP.name());
        if (StringUtils.isNotBlank(phoneNumber)) {
            sql.append(" and cm.phone_number = ?");
            params.add(phoneNumber.trim());
        }
        if (clientId != null) {
            sql.append(" and cm.client_id = ?");
            params.add(clientId);
        }
        if (StringUtils.isNotBlank(text)) {
            sql.append(" and cm.message_body like ?");
            params.add("%" + text.trim() + "%");
        }
        if (fromDate != null) {
            sql.append(" and cm.created_date >= ?");
            params.add(fromDate.atStartOfDay());
        }
        if (toDate != null) {
            sql.append(" and cm.created_date < ?");
            params.add(toDate.plusDays(1).atStartOfDay());
        }
        if (StringUtils.isNotBlank(recipientType)) {
            sql.append(" and cm.recipient_type = ?");
            params.add(recipientType.trim().toUpperCase());
        }
        sql.append(" order by cm.created_date desc limit ?");
        params.add(limit == null ? 100 : Math.min(limit, 500));
        return jdbcTemplate.query(sql.toString(), new ConversationSearchMapper(), params.toArray());
    }

    private static final class ConversationSearchMapper implements RowMapper<WhatsAppConversationSearchResultData> {

        @Override
        public WhatsAppConversationSearchResultData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final WhatsAppConversationSearchResultData data = new WhatsAppConversationSearchResultData();
            data.setId(rs.getLong("id"));
            data.setPhoneNumber(rs.getString("phone_number"));
            data.setClientId(JdbcSupport.getLong(rs, "client_id"));
            data.setClientDisplayName(rs.getString("display_name"));
            data.setDirection(CommunicationDirection.valueOf(rs.getString("direction")));
            data.setRecipientType(RecipientType.valueOf(rs.getString("recipient_type")));
            data.setMessageBody(rs.getString("message_body"));
            data.setCreatedDate(JdbcSupport.getLocalDateTime(rs, "created_date"));
            return data;
        }
    }
}

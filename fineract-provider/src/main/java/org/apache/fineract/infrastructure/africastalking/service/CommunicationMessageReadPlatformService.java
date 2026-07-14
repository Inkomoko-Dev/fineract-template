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
package org.apache.fineract.infrastructure.africastalking.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.africastalking.data.CommunicationMessageData;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationChannel;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationDirection;
import org.apache.fineract.infrastructure.africastalking.domain.CommunicationMessageStatus;
import org.apache.fineract.infrastructure.africastalking.domain.RecipientType;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommunicationMessageReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final CommunicationMessageMapper mapper = new CommunicationMessageMapper();

    public List<CommunicationMessageData> retrieveWhatsAppMessages() {
        final String sql = "select " + mapper.schema() + " where cm.channel = ? order by cm.created_date desc";
        return this.jdbcTemplate.query(sql, this.mapper, CommunicationChannel.WHATSAPP.name());
    }

    public CommunicationMessageData retrieveOne(final Long messageId) {
        final String sql = "select " + mapper.schema() + " where cm.id = ?";
        final List<CommunicationMessageData> results = this.jdbcTemplate.query(sql, this.mapper, messageId);
        return results.isEmpty() ? null : results.get(0);
    }

    private static final class CommunicationMessageMapper implements RowMapper<CommunicationMessageData> {

        public String schema() {
            return "cm.id as id, cm.external_id as externalId, cm.channel as channel, cm.direction as direction, "
                    + "cm.recipient_type as recipientType, cm.client_id as clientId, cm.staff_id as staffId, "
                    + "cm.phone_number as phoneNumber, cm.message_body as messageBody, cm.template_name as templateName, "
                    + "cm.status as status, cm.status_detail as statusDetail, cm.created_date as createdDate, "
                    + "cm.delivered_date as deliveredDate, cm.read_date as readDate from communication_message cm";
        }

        @Override
        public CommunicationMessageData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            return CommunicationMessageData.instance(rs.getLong("id"), rs.getString("externalId"),
                    CommunicationChannel.valueOf(rs.getString("channel")), CommunicationDirection.valueOf(rs.getString("direction")),
                    RecipientType.valueOf(rs.getString("recipientType")), JdbcSupport.getLong(rs, "clientId"),
                    JdbcSupport.getLong(rs, "staffId"), rs.getString("phoneNumber"), rs.getString("messageBody"),
                    rs.getString("templateName"), CommunicationMessageStatus.valueOf(rs.getString("status")), rs.getString("statusDetail"),
                    JdbcSupport.getLocalDateTime(rs, "createdDate"), JdbcSupport.getLocalDateTime(rs, "deliveredDate"),
                    JdbcSupport.getLocalDateTime(rs, "readDate"));
        }
    }
}

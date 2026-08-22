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
package org.apache.fineract.portfolio.client.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.portfolio.client.data.PartnerClientData;
import org.apache.fineract.portfolio.client.data.PartnerClientHistoryData;
import org.apache.fineract.portfolio.client.domain.PartnerClientMapping;
import org.apache.fineract.portfolio.client.domain.PartnerClientMappingHistory;
import org.apache.fineract.portfolio.client.domain.PartnerClientMappingHistoryRepository;
import org.apache.fineract.portfolio.client.domain.PartnerClientMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(readOnly = true)
public class PartnerClientReadPlatformServiceImpl implements PartnerClientReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseSpecificSQLGenerator sqlGenerator;
    private final PaginationHelper paginationHelper;
    private final PartnerClientMappingRepository partnerClientMappingRepository;
    private final PartnerClientMappingHistoryRepository partnerClientMappingHistoryRepository;

    @Autowired
    public PartnerClientReadPlatformServiceImpl(final JdbcTemplate jdbcTemplate,
            final DatabaseSpecificSQLGenerator sqlGenerator,
            final PaginationHelper paginationHelper,
            final PartnerClientMappingRepository partnerClientMappingRepository,
            final PartnerClientMappingHistoryRepository partnerClientMappingHistoryRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlGenerator = sqlGenerator;
        this.paginationHelper = paginationHelper;
        this.partnerClientMappingRepository = partnerClientMappingRepository;
        this.partnerClientMappingHistoryRepository = partnerClientMappingHistoryRepository;
    }

    @Override
    public Page<PartnerClientData> retrieveAllPartnerClients(final String partnerCode, final Integer status, final Long officeId,
            final LocalDate fromDate, final LocalDate toDate, final Integer offset, final Integer limit) {
        final int safeOffset = offset == null || offset < 0 ? 0 : offset;
        final int safeLimit = limit == null || limit <= 0 ? 15 : Math.min(limit, 200);

        final StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("select ").append(this.sqlGenerator.calcFoundRows()).append(" ");
        sqlBuilder.append(" pcm.id as mappingId, pcm.client_id as clientId, pcm.partner_code as partnerCode, ");
        sqlBuilder.append(" pcm.assigned_date as assignedDate, pcm.assigned_by as assignedById, pcm.is_active as isActive, ");
        sqlBuilder.append(" pcm.created_date as createdDate, pcm.updated_date as updatedDate, ");
        sqlBuilder.append(" c.id as id, c.account_no as accountNo, c.display_name as displayName, ");
        sqlBuilder.append(" c.firstname as firstname, c.lastname as lastname, c.mobile_no as mobileNo, ");
        sqlBuilder.append(" c.email_address as emailAddress, c.status_enum as status, c.activation_date as activationDate, ");
        sqlBuilder.append(" c.office_joining_date as officeJoiningDate, c.office_id as officeId, o.name as officeName, ");
        sqlBuilder.append(" u.username as assignedByName ");
        sqlBuilder.append(" from x_partner_client_mapping pcm ");
        sqlBuilder.append(" join m_client c on c.id = pcm.client_id ");
        sqlBuilder.append(" left join m_office o on o.id = c.office_id ");
        sqlBuilder.append(" left join m_appuser u on u.id = pcm.assigned_by ");
        sqlBuilder.append(" where pcm.partner_code = ? ");
        sqlBuilder.append(" and pcm.is_active = true ");

        final List<Object> params = new ArrayList<>();
        params.add(partnerCode);

        if (status != null) {
            sqlBuilder.append(" and c.status_enum = ? ");
            params.add(status);
        }

        if (officeId != null) {
            sqlBuilder.append(" and c.office_id = ? ");
            params.add(officeId);
        }

        if (fromDate != null) {
            sqlBuilder.append(" and pcm.assigned_date >= ? ");
            params.add(fromDate);
        }

        if (toDate != null) {
            sqlBuilder.append(" and pcm.assigned_date <= ? ");
            params.add(toDate);
        }

        sqlBuilder.append(" order by pcm.assigned_date desc, pcm.id desc ");
        sqlBuilder.append(this.sqlGenerator.limit(safeLimit, safeOffset));

        return this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), params.toArray(), new PartnerClientMapper());
    }

    @Override
    public PartnerClientData retrievePartnerClientByPhone(final String phoneNumber, final String partnerCode) {
        final PartnerClientMapping mapping = this.partnerClientMappingRepository
                .findByClientPhoneNumberAndPartnerCode(phoneNumber, partnerCode).orElse(null);

        if (mapping == null) {
            return null;
        }

        return convertToPartnerClientData(mapping);
    }

    @Override
    public PartnerClientData retrievePartnerClient(final Long clientId, final String partnerCode) {
        final PartnerClientMapping mapping = this.partnerClientMappingRepository
                .findByClientIdAndPartnerCodeAndIsActiveTrue(clientId, partnerCode).orElse(null);

        if (mapping == null) {
            return null;
        }

        return convertToPartnerClientData(mapping);
    }

    @Override
    public PartnerClientData retrievePartnerClientForAdmin(final Long clientId) {
        final PartnerClientMapping mapping = this.partnerClientMappingRepository
                .findByClientIdAndIsActiveTrue(clientId).orElse(null);

        if (mapping == null) {
            return null;
        }

        return convertToPartnerClientData(mapping);
    }

    @Override
    public List<PartnerClientHistoryData> retrieveClientMappingHistory(final Long clientId) {
        final List<PartnerClientMappingHistory> historyEntries = this.partnerClientMappingHistoryRepository
                .findByClientIdOrderByChangedDateDesc(clientId);

        final List<PartnerClientHistoryData> historyData = new ArrayList<>();
        for (final PartnerClientMappingHistory history : historyEntries) {
            historyData.add(convertHistoryToPartnerClientData(history));
        }

        return historyData;
    }

    private PartnerClientData convertToPartnerClientData(final PartnerClientMapping mapping) {
        try {
            // Use the existing RowMapper for consistency
            final String sql = "select pcm.id as mappingId, pcm.client_id as clientId, pcm.partner_code as partnerCode, "
                    + " pcm.assigned_date as assignedDate, pcm.assigned_by as assignedById, pcm.is_active as isActive, "
                    + " pcm.created_date as createdDate, pcm.updated_date as updatedDate, "
                    + " c.id as id, c.account_no as accountNo, c.display_name as displayName, "
                    + " c.firstname as firstname, c.lastname as lastname, c.mobile_no as mobileNo, "
                    + " c.email_address as emailAddress, c.status_enum as status, c.activation_date as activationDate, "
                    + " c.office_joining_date as officeJoiningDate, c.office_id as officeId, o.name as officeName, "
                    + " u.username as assignedByName "
                    + " from x_partner_client_mapping pcm "
                    + " join m_client c on c.id = pcm.client_id "
                    + " left join m_office o on o.id = c.office_id "
                    + " left join m_appuser u on u.id = pcm.assigned_by "
                    + " where pcm.id = ?";

            final List<PartnerClientData> results = this.jdbcTemplate.query(sql, 
                new Object[]{mapping.getId()}, 
                new PartnerClientMapper()
            );
            
            return results.isEmpty() ? null : results.get(0);
        } catch (final Exception e) {
            log.error("Error converting mapping to PartnerClientData for client {}: {}", mapping.getClientId(), e.getMessage(), e);
            return null;
        }
    }

    private PartnerClientHistoryData convertHistoryToPartnerClientData(final PartnerClientMappingHistory history) {
        try {
            return PartnerClientHistoryData.from(
                history.getId(),
                history.getMappingId(),
                history.getClientId(),
                history.getPartnerCode(),
                history.getActionType(),
                history.getPreviousPartnerCode(),
                history.getNewPartnerCode(),
                history.getChangedDate(),
                history.getChangedById(),
                history.getChangedByName(),
                history.getReason()
            );
        } catch (final Exception e) {
            log.error("Error converting history to PartnerClientHistoryData for client {}: {}", history.getClientId(), e.getMessage(), e);
            return null;
        }
    }



    private static final class PartnerClientMapper implements RowMapper<PartnerClientData> {

        @Override
        public PartnerClientData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            final Integer status = JdbcSupport.getInteger(rs, "status");
            final LocalDate activationDate = JdbcSupport.getLocalDate(rs, "activationDate");
            final LocalDate officeJoiningDate = JdbcSupport.getLocalDate(rs, "officeJoiningDate");
            final LocalDateTime createdDate = JdbcSupport.getLocalDateTime(rs, "createdDate");
            final LocalDateTime updatedDate = JdbcSupport.getLocalDateTime(rs, "updatedDate");
            
            return new PartnerClientData(
                JdbcSupport.getLong(rs, "id"),
                rs.getString("accountNo"),
                rs.getString("displayName"),
                rs.getString("firstname"),
                rs.getString("lastname"),
                rs.getString("mobileNo"),
                rs.getString("emailAddress"),
                status,
                activationDate,
                officeJoiningDate,
                JdbcSupport.getLong(rs, "officeId"),
                rs.getString("officeName"),
                rs.getString("partnerCode"),
                JdbcSupport.getLocalDate(rs, "assignedDate"),
                JdbcSupport.getLong(rs, "assignedById"),
                rs.getString("assignedByName"),
                rs.getBoolean("isActive"),
                createdDate,
                updatedDate
            );
        }
    }
}

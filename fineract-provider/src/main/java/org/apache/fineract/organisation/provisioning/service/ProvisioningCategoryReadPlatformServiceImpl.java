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
package org.apache.fineract.organisation.provisioning.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.organisation.provisioning.data.ProvisioningCategoryData;
import org.apache.fineract.organisation.provisioning.exception.ProvisioningCategoryNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class ProvisioningCategoryReadPlatformServiceImpl implements ProvisioningCategoryReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final ProvisioningCategoryRowMapper provisionCategoryRowMapper;

    @Autowired
    public ProvisioningCategoryReadPlatformServiceImpl(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.provisionCategoryRowMapper = new ProvisioningCategoryRowMapper();
    }

    @Override
    public Collection<ProvisioningCategoryData> retrieveAllProvisionCategories() {
        final String sql = "select " + this.provisionCategoryRowMapper.schema() + " from m_provision_category pc order by pc.display_order, pc.id";
        return this.jdbcTemplate.query(sql, this.provisionCategoryRowMapper); // NOSONAR
    }

    @Override
    public Collection<ProvisioningCategoryData> retrieveActiveProvisionCategories() {
        final String sql = "select " + this.provisionCategoryRowMapper.schema()
                + " from m_provision_category pc where pc.is_active = true order by pc.display_order, pc.id";
        return this.jdbcTemplate.query(sql, this.provisionCategoryRowMapper); // NOSONAR
    }

    @Override
    public ProvisioningCategoryData retrieveProvisionCategory(final Long categoryId) {
        final String sql = "select " + this.provisionCategoryRowMapper.schema() + " from m_provision_category pc where pc.id = ?";
        try {
            return this.jdbcTemplate.queryForObject(sql, this.provisionCategoryRowMapper, categoryId); // NOSONAR
        } catch (EmptyResultDataAccessException e) {
            throw new ProvisioningCategoryNotFoundException(categoryId);
        }
    }

    private static final class ProvisioningCategoryRowMapper implements RowMapper<ProvisioningCategoryData> {

        @Override
        public ProvisioningCategoryData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            final Long id = JdbcSupport.getLong(rs, "id");
            final String categoryName = rs.getString("category_name");
            final String description = rs.getString("description");
            final String categoryCode = rs.getString("category_code");
            final Integer displayOrder = JdbcSupport.getInteger(rs, "display_order");
            final Boolean active = rs.getBoolean("is_active");
            return new ProvisioningCategoryData(id, categoryName, description, categoryCode, displayOrder, active);
        }

        public String schema() {
            return " pc.id as id, pc.category_name as category_name, pc.description as description, pc.category_code as category_code, "
                    + "pc.display_order as display_order, pc.is_active as is_active";
        }
    }
}

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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.glaccount.data.GLAccountData;
import org.apache.fineract.accounting.glaccount.service.GLAccountReadPlatformService;
import org.apache.fineract.organisation.provisioning.data.ProvisioningCategoryData;
import org.apache.fineract.organisation.provisioning.data.ProvisioningCriteriaData;
import org.apache.fineract.organisation.provisioning.data.ProvisioningCriteriaDefinitionData;
import org.apache.fineract.organisation.provisioning.data.ProvisioningCriteriaVersionData;
import org.apache.fineract.organisation.provisioning.exception.ProvisioningCriteriaNotFoundException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanproduct.data.LoanProductData;
import org.apache.fineract.portfolio.loanproduct.service.LoanProductReadPlatformService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class ProvisioningCriteriaReadPlatformServiceImpl implements ProvisioningCriteriaReadPlatformService {

    private final JdbcTemplate jdbcTemplate;
    private final ProvisioningCategoryReadPlatformService provisioningCategoryReadPlatformService;
    private final LoanProductReadPlatformService loanProductReadPlatformService;
    private final GLAccountReadPlatformService glAccountReadPlatformService;
    private final LoanProductReadPlatformService loanProductReaPlatformService;

    @Autowired
    public ProvisioningCriteriaReadPlatformServiceImpl(final JdbcTemplate jdbcTemplate,
            final ProvisioningCategoryReadPlatformService provisioningCategoryReadPlatformService,
            final LoanProductReadPlatformService loanProductReadPlatformService,
            final GLAccountReadPlatformService glAccountReadPlatformService,
            final LoanProductReadPlatformService loanProductReaPlatformService) {
        this.jdbcTemplate = jdbcTemplate;
        this.provisioningCategoryReadPlatformService = provisioningCategoryReadPlatformService;
        this.loanProductReadPlatformService = loanProductReadPlatformService;
        this.glAccountReadPlatformService = glAccountReadPlatformService;
        this.loanProductReaPlatformService = loanProductReaPlatformService;
    }

    @Override
    public ProvisioningCriteriaData retrievePrivisiongCriteriaTemplate() {
        boolean onlyActive = true;
        final Collection<ProvisioningCategoryData> categories = this.provisioningCategoryReadPlatformService
                .retrieveActiveProvisionCategories();
        final Collection<LoanProductData> allLoanProducts = this.loanProductReadPlatformService
                .retrieveAllLoanProductsForLookup(onlyActive);
        final Collection<GLAccountData> glAccounts = this.glAccountReadPlatformService.retrieveAllEnabledDetailGLAccounts();
        return ProvisioningCriteriaData.toTemplate(categories, buildDefinitionStubsFromCategories(categories), allLoanProducts, glAccounts);
    }

    /** One editable row per active category for provisioning criteria wizard (min/max percentages still user-entered). */
    private List<ProvisioningCriteriaDefinitionData> buildDefinitionStubsFromCategories(Collection<ProvisioningCategoryData> categories) {
        List<ProvisioningCategoryData> sorted = new ArrayList<>(categories);
        sorted.sort(Comparator.comparing((ProvisioningCategoryData c) -> c.getDisplayOrder() == null ? Integer.MAX_VALUE : c.getDisplayOrder())
                .thenComparingLong(ProvisioningCategoryData::getId));
        List<ProvisioningCriteriaDefinitionData> stubs = new ArrayList<>();
        for (ProvisioningCategoryData cat : sorted) {
            stubs.add(ProvisioningCriteriaDefinitionData.template(cat.getId(), cat.getCategoryCode(), cat.getCategoryName(), cat.getDisplayOrder()));
        }
        return stubs;
    }

    @Override
    public ProvisioningCriteriaData retrievePrivisiongCriteriaTemplate(ProvisioningCriteriaData data) {
        boolean onlyActive = true;
        final Collection<ProvisioningCategoryData> categories = mergeTemplateCategories(data,
                this.provisioningCategoryReadPlatformService.retrieveActiveProvisionCategories());
        final Collection<LoanProductData> allLoanProducts = this.loanProductReadPlatformService
                .retrieveAllLoanProductsForLookup(onlyActive);
        final Collection<GLAccountData> glAccounts = this.glAccountReadPlatformService.retrieveAllEnabledDetailGLAccounts();
        return ProvisioningCriteriaData.toTemplate(data, categories, allLoanProducts, glAccounts);
    }

    private Collection<ProvisioningCategoryData> mergeTemplateCategories(ProvisioningCriteriaData data,
            Collection<ProvisioningCategoryData> activeCategories) {
        Map<Long, ProvisioningCategoryData> merged = new LinkedHashMap<>();
        for (ProvisioningCategoryData category : activeCategories) {
            merged.put(category.getId(), category);
        }
        if (data.getDefinitions() != null) {
            for (ProvisioningCriteriaDefinitionData definition : data.getDefinitions()) {
                if (definition.getCategoryId() != null && !merged.containsKey(definition.getCategoryId())) {
                    merged.put(definition.getCategoryId(), new ProvisioningCategoryData(definition.getCategoryId(),
                            definition.getCategoryName(), null, definition.getCategoryCode(), definition.getDisplayOrder(), Boolean.FALSE));
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public Collection<ProvisioningCriteriaData> retrieveAllProvisioningCriterias() {
        ProvisioningCriteriaRowMapper mapper = new ProvisioningCriteriaRowMapper();
        final String sql = "select " + mapper.schema();
        return this.jdbcTemplate.query(sql, mapper); // NOSONAR
    }

    private static final class ProvisioningCriteriaRowMapper implements RowMapper<ProvisioningCriteriaData> {

        @Override
        public ProvisioningCriteriaData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            Long criteriaId = rs.getLong("id");
            String criteriaName = rs.getString("criteriaName");
            String createdBy = rs.getString("username");
            return ProvisioningCriteriaData.toLookup(criteriaId, criteriaName, createdBy);
        }

        public String schema() {
            return "mpc.id as id, mpc.criteria_name as criteriaName, appu.username as username from m_provisioning_criteria as mpc LEFT JOIN m_appuser appu on mpc.createdby_id=appu.id";
        }
    }

    @Override
    public ProvisioningCriteriaData retrieveProvisioningCriteria(Long criteriaId) {
        try {
            LocalDate businessDate = DateUtils.getBusinessLocalDate();
            CriteriaHeaderData publishedHeader = retrievePublishedCriteriaHeader(criteriaId);
            CriteriaHeaderData effectiveHeader = retrieveEffectiveCriteriaHeader(criteriaId, businessDate);
            if (publishedHeader == null) {
                throw new ProvisioningCriteriaNotFoundException(criteriaId);
            }
            if (effectiveHeader == null) {
                effectiveHeader = publishedHeader;
            }
            Collection<LoanProductData> loanProducts = loanProductReaPlatformService.retrieveAllLoanProductsForLookup(
                    "select product_id from m_loanproduct_provisioning_mapping where m_loanproduct_provisioning_mapping.criteria_id="
                            + criteriaId);
            List<ProvisioningCriteriaDefinitionData> publishedDefinitions = retrieveProvisioningDefinitions(publishedHeader.activeVersionId);
            List<ProvisioningCriteriaDefinitionData> effectiveDefinitions = retrieveProvisioningDefinitions(effectiveHeader.activeVersionId);
            String versionDisplayStatus = resolveVersionDisplayStatus(publishedHeader.effectiveFrom, businessDate);
            return ProvisioningCriteriaData.toLookup(criteriaId, publishedHeader.criteriaName, loanProducts, publishedDefinitions,
                    publishedHeader.activeVersionId, publishedHeader.versionNo, publishedHeader.effectiveFrom,
                    publishedHeader.policyChangeReason, effectiveHeader.activeVersionId, effectiveHeader.versionNo,
                    effectiveHeader.effectiveFrom, versionDisplayStatus, effectiveDefinitions);
        } catch (EmptyResultDataAccessException e) {
            throw new ProvisioningCriteriaNotFoundException(criteriaId, e);
        }

    }

    @Override
    public List<ProvisioningCriteriaVersionData> retrieveAllCriteriaVersions(final Long criteriaId) {
        if (retrievePublishedCriteriaHeader(criteriaId) == null) {
            throw new ProvisioningCriteriaNotFoundException(criteriaId);
        }
        VersionRowMapper rowMapper = new VersionRowMapper();
        final String sql = "select " + rowMapper.schema()
                + " where pc.id = ? order by pcv.version_no desc";
        return this.jdbcTemplate.query(sql, rowMapper, criteriaId); // NOSONAR
    }

    @Override
    public ProvisioningCriteriaVersionData retrieveCriteriaVersion(final Long criteriaId, final Long versionId) {
        CriteriaHeaderData publishedHeader = retrievePublishedCriteriaHeader(criteriaId);
        if (publishedHeader == null) {
            throw new ProvisioningCriteriaNotFoundException(criteriaId);
        }
        VersionRowMapper rowMapper = new VersionRowMapper();
        final String sql = "select " + rowMapper.schema() + " where pc.id = ? and pcv.id = ?";
        ProvisioningCriteriaVersionData version;
        try {
            version = this.jdbcTemplate.queryForObject(sql, rowMapper, criteriaId, versionId); // NOSONAR
        } catch (EmptyResultDataAccessException e) {
            throw new ProvisioningCriteriaNotFoundException(criteriaId, e);
        }
        List<ProvisioningCriteriaDefinitionData> definitions = retrieveProvisioningDefinitions(versionId);
        ProvisioningCriteriaVersionData previousVersion = null;
        if (version.getVersionNo() != null && version.getVersionNo() > 1) {
            final String previousSql = "select " + rowMapper.schema() + " where pc.id = ? and pcv.version_no = ?";
            try {
                ProvisioningCriteriaVersionData previousHeader = this.jdbcTemplate.queryForObject(previousSql, rowMapper, criteriaId,
                        version.getVersionNo() - 1); // NOSONAR
                List<ProvisioningCriteriaDefinitionData> previousDefinitions = retrieveProvisioningDefinitions(previousHeader.getId());
                previousVersion = new ProvisioningCriteriaVersionData(previousHeader.getId(), previousHeader.getCriteriaId(),
                        previousHeader.getCriteriaName(), previousHeader.getVersionNo(), previousHeader.getEffectiveFrom(),
                        previousHeader.getRetiredOn(), previousHeader.getPolicyChangeReason(), previousHeader.getCreatedBy(),
                        previousHeader.getCreatedDate(), previousDefinitions, null);
            } catch (EmptyResultDataAccessException ignored) {
                previousVersion = null;
            }
        }
        return new ProvisioningCriteriaVersionData(version.getId(), version.getCriteriaId(), version.getCriteriaName(),
                version.getVersionNo(), version.getEffectiveFrom(), version.getRetiredOn(), version.getPolicyChangeReason(),
                version.getCreatedBy(), version.getCreatedDate(), definitions, previousVersion);
    }

    private String resolveVersionDisplayStatus(final LocalDate publishedEffectiveFrom, final LocalDate businessDate) {
        if (publishedEffectiveFrom == null || businessDate == null) {
            return "ACTIVE";
        }
        return publishedEffectiveFrom.isAfter(businessDate) ? "SCHEDULED" : "ACTIVE";
    }

    private List<ProvisioningCriteriaDefinitionData> retrieveProvisioningDefinitions(Long criteriaVersionId) {
        ProvisioningCriteriaDefinitionRowMapper rowMapper = new ProvisioningCriteriaDefinitionRowMapper();
        final String sql = "select " + rowMapper.schema() + " where pc.criteria_version_id = ? order by pc.display_order, pc.min_age";
        return this.jdbcTemplate.query(sql, rowMapper, new Object[] { criteriaVersionId }); // NOSONAR
    }

    private static final class ProvisioningCriteriaDefinitionRowMapper implements RowMapper<ProvisioningCriteriaDefinitionData> {

        private final StringBuilder sqlQuery = new StringBuilder()
                .append("pc.id, pc.criteria_id, pc.category_id, pc.category_code, pc.category_name, pc.display_order, pc.min_age, pc.max_age, ")
                .append("pc.provision_percentage, pc.liability_account, pc.expense_account, lia.gl_code as liabilitycode, expe.gl_code as expensecode, ")
                .append("lia.name as liabilityname, expe.name as expensename ").append("from m_provisioning_criteria_definition as pc ")
                .append("LEFT JOIN acc_gl_account lia ON lia.id = pc.liability_account ")
                .append("LEFT JOIN acc_gl_account expe ON expe.id = pc.expense_account ");

        @Override
        public ProvisioningCriteriaDefinitionData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum)
                throws SQLException {
            Long id = rs.getLong("id");
            Long categoryId = rs.getLong("category_id");
            String categoryCode = rs.getString("category_code");
            String categoryName = rs.getString("category_name");
            Integer displayOrder = rs.getInt("display_order");
            Long minAge = rs.getLong("min_age");
            Long maxAge = rs.getObject("max_age") == null ? null : rs.getLong("max_age");
            BigDecimal provisioningPercentage = rs.getBigDecimal("provision_percentage");
            Long liabilityAccount = rs.getLong("liability_account");
            String liabilityAccountCode = rs.getString("liabilitycode");
            String liabilityAccountName = rs.getString("liabilityname");
            Long expenseAccount = rs.getLong("expense_account");
            String expenseAccountCode = rs.getString("expensecode");
            String expenseAccountName = rs.getString("expensename");

            return new ProvisioningCriteriaDefinitionData(id, categoryId, categoryCode, categoryName, displayOrder, minAge, maxAge,
                    provisioningPercentage, liabilityAccount, liabilityAccountCode, liabilityAccountName, expenseAccount,
                    expenseAccountCode, expenseAccountName);
        }

        public String schema() {
            return sqlQuery.toString();
        }
    }

    private CriteriaHeaderData retrievePublishedCriteriaHeader(Long criteriaId) {
        CriteriaHeaderRowMapper rowMapper = new CriteriaHeaderRowMapper();
        final String sql = "select " + rowMapper.schema() + " where pc.id = ? order by pcv.version_no desc limit 1";
        try {
            return this.jdbcTemplate.queryForObject(sql, rowMapper, criteriaId); // NOSONAR
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private CriteriaHeaderData retrieveEffectiveCriteriaHeader(Long criteriaId, LocalDate businessDate) {
        CriteriaHeaderRowMapper rowMapper = new CriteriaHeaderRowMapper();
        final String sql = "select " + rowMapper.schema()
                + " where pc.id = ? and pcv.effective_from <= ? and (pcv.retired_on is null or pcv.retired_on >= ?)"
                + " order by pcv.version_no desc limit 1";
        try {
            return this.jdbcTemplate.queryForObject(sql, rowMapper, criteriaId, businessDate, businessDate); // NOSONAR
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private static final class VersionRowMapper implements RowMapper<ProvisioningCriteriaVersionData> {

        @Override
        public ProvisioningCriteriaVersionData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum)
                throws SQLException {
            LocalDateTime createdDate = rs.getTimestamp("createdDate") == null ? null : rs.getTimestamp("createdDate").toLocalDateTime();
            return new ProvisioningCriteriaVersionData(rs.getLong("versionId"), rs.getLong("criteriaId"), rs.getString("criteriaName"),
                    rs.getInt("versionNo"),
                    rs.getDate("effectiveFrom") == null ? null : rs.getDate("effectiveFrom").toLocalDate(),
                    rs.getDate("retiredOn") == null ? null : rs.getDate("retiredOn").toLocalDate(), rs.getString("policyChangeReason"),
                    rs.getString("createdBy"), createdDate);
        }

        public String schema() {
            return "pc.id as criteriaId, pc.criteria_name as criteriaName, pcv.id as versionId, pcv.version_no as versionNo, "
                    + "pcv.effective_from as effectiveFrom, pcv.retired_on as retiredOn, pcv.policy_change_reason as policyChangeReason, "
                    + "appu.username as createdBy, COALESCE(pcv.created_date, pcv.lastmodified_date) as createdDate from m_provisioning_criteria pc "
                    + "join m_provisioning_criteria_version pcv on pcv.criteria_id = pc.id "
                    + "left join m_appuser appu on appu.id = pcv.createdby_id";
        }
    }

    private static final class CriteriaHeaderRowMapper implements RowMapper<CriteriaHeaderData> {

        @Override
        public CriteriaHeaderData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return new CriteriaHeaderData(rs.getLong("criteriaId"), rs.getString("criteriaName"), rs.getLong("activeVersionId"),
                    rs.getInt("versionNo"), rs.getDate("effectiveFrom").toLocalDate(), rs.getString("policyChangeReason"));
        }

        public String schema() {
            return "pc.id as criteriaId, pc.criteria_name as criteriaName, pcv.id as activeVersionId, pcv.version_no as versionNo, "
                    + "pcv.effective_from as effectiveFrom, pcv.policy_change_reason as policyChangeReason from m_provisioning_criteria pc "
                    + "join m_provisioning_criteria_version pcv on pcv.criteria_id = pc.id";
        }
    }

    private static final class CriteriaHeaderData {

        private final Long criteriaId;
        private final String criteriaName;
        private final Long activeVersionId;
        private final Integer versionNo;
        private final java.time.LocalDate effectiveFrom;
        private final String policyChangeReason;

        private CriteriaHeaderData(Long criteriaId, String criteriaName, Long activeVersionId, Integer versionNo,
                java.time.LocalDate effectiveFrom, String policyChangeReason) {
            this.criteriaId = criteriaId;
            this.criteriaName = criteriaName;
            this.activeVersionId = activeVersionId;
            this.versionNo = versionNo;
            this.effectiveFrom = effectiveFrom;
            this.policyChangeReason = policyChangeReason;
        }
    }
}

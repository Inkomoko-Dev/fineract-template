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
package org.apache.fineract.notification.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.notification.cache.CacheNotificationResponseHeader;
import org.apache.fineract.notification.data.NotificationData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationReadPlatformServiceImpl implements NotificationReadPlatformService {

    /** Avoid hitting notification_mapper on every authenticated request; UI badge can lag by this window. */
    private static final long UNREAD_CACHE_TTL_SECONDS = 30L;

    private final ConcurrentHashMap<Long, ConcurrentHashMap<Long, CacheNotificationResponseHeader>> tenantNotificationResponseHeaderCache = new ConcurrentHashMap<>();

    private final NotificationDataRow notificationDataRow = new NotificationDataRow();

    private final JdbcTemplate jdbcTemplate;
    private final PlatformSecurityContext context;
    private final ColumnValidator columnValidator;
    private final PaginationHelper paginationHelper;
    private final DatabaseSpecificSQLGenerator sqlGenerator;

    @Override
    public boolean hasUnreadNotifications(Long appUserId) {
        final Long tenantId = ThreadLocalContextUtil.getTenant().getId();
        final long now = System.currentTimeMillis() / 1000L;
        final ConcurrentHashMap<Long, CacheNotificationResponseHeader> notificationResponseHeaderCache = this.tenantNotificationResponseHeaderCache
                .computeIfAbsent(tenantId, id -> new ConcurrentHashMap<>());

        final CacheNotificationResponseHeader cached = notificationResponseHeaderCache.get(appUserId);
        if (cached != null && cached.getLastFetch() != null && (now - cached.getLastFetch()) <= UNREAD_CACHE_TTL_SECONDS) {
            return cached.hasNotifications();
        }
        return createUpdateCacheValue(appUserId, now, notificationResponseHeaderCache);
    }

    private boolean createUpdateCacheValue(Long appUserId, Long now,
            ConcurrentHashMap<Long, CacheNotificationResponseHeader> notificationResponseHeaderCache) {
        final boolean hasNotifications = checkForUnreadNotifications(appUserId);
        notificationResponseHeaderCache.put(appUserId, new CacheNotificationResponseHeader(hasNotifications, now));
        return hasNotifications;
    }

    private boolean checkForUnreadNotifications(Long appUserId) {
        // Existence check only — never materialize every unread row for the auth-filter header.
        final String sql = "SELECT 1 FROM notification_mapper WHERE user_id = ? AND is_read = false " + sqlGenerator.limit(1);
        final Boolean found = this.jdbcTemplate.query(sql, rs -> rs.next() ? Boolean.TRUE : Boolean.FALSE, appUserId);
        return Boolean.TRUE.equals(found);
    }

    @Override
    public void updateNotificationReadStatus() {
        final Long appUserId = context.authenticatedUser().getId();
        final String sql = "UPDATE notification_mapper SET is_read = true WHERE is_read = false and user_id = ?";
        this.jdbcTemplate.update(sql, appUserId);
        invalidateUnreadCache(appUserId);
    }

    private void invalidateUnreadCache(Long appUserId) {
        final Long tenantId = ThreadLocalContextUtil.getTenant().getId();
        final ConcurrentHashMap<Long, CacheNotificationResponseHeader> cache = this.tenantNotificationResponseHeaderCache.get(tenantId);
        if (cache != null) {
            cache.remove(appUserId);
        }
    }

    @Override
    public Page<NotificationData> getAllUnreadNotifications(final SearchParameters searchParameters) {
        final Long appUserId = context.authenticatedUser().getId();
        String sql = "SELECT " + sqlGenerator.calcFoundRows() + " ng.id as id, nm.user_id as userId, ng.object_type as objectType, "
                + "ng.object_identifier as objectId, ng.actor as actor, ng." + sqlGenerator.escape("action")
                + " as action, ng.notification_content "
                + "as content, ng.is_system_generated as isSystemGenerated, nm.created_at as createdAt "
                + "FROM notification_mapper nm INNER JOIN notification_generator ng ON nm.notification_id = ng.id "
                + "WHERE nm.user_id = ? AND nm.is_read = false order by nm.created_at desc";

        return getNotificationDataPage(searchParameters, appUserId, sql);
    }

    @Override
    public Page<NotificationData> getAllNotifications(SearchParameters searchParameters) {
        final Long appUserId = context.authenticatedUser().getId();
        String sql = "SELECT " + sqlGenerator.calcFoundRows() + " ng.id as id, nm.user_id as userId, ng.object_type as objectType, "
                + "ng.object_identifier as objectId, ng.actor as actor, ng." + sqlGenerator.escape("action")
                + " as action, ng.notification_content "
                + "as content, ng.is_system_generated as isSystemGenerated, nm.created_at as createdAt "
                + "FROM notification_mapper nm INNER JOIN notification_generator ng ON nm.notification_id = ng.id "
                + "WHERE nm.user_id = ? order by nm.created_at desc";

        return getNotificationDataPage(searchParameters, appUserId, sql);
    }

    private Page<NotificationData> getNotificationDataPage(SearchParameters searchParameters, Long appUserId, String sql) {
        final StringBuilder sqlBuilder = new StringBuilder(200);
        sqlBuilder.append(sql);

        if (searchParameters.isOrderByRequested()) {
            sqlBuilder.append(" order by ").append(searchParameters.getOrderBy());
            this.columnValidator.validateSqlInjection(sqlBuilder.toString(), searchParameters.getOrderBy());
            if (searchParameters.isSortOrderProvided()) {
                sqlBuilder.append(' ').append(searchParameters.getSortOrder());
                this.columnValidator.validateSqlInjection(sqlBuilder.toString(), searchParameters.getSortOrder());
            }
        }

        if (searchParameters.isLimited()) {
            sqlBuilder.append(" ");
            if (searchParameters.isOffset()) {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit(), searchParameters.getOffset()));
            } else {
                sqlBuilder.append(sqlGenerator.limit(searchParameters.getLimit()));
            }
        }

        Object[] params = new Object[] { appUserId };
        return this.paginationHelper.fetchPage(this.jdbcTemplate, sqlBuilder.toString(), params, this.notificationDataRow);
    }

    private static final class NotificationDataRow implements RowMapper<NotificationData> {

        @Override
        public NotificationData mapRow(ResultSet rs, int rowNum) throws SQLException {
            NotificationData notificationData = new NotificationData();

            final Long id = rs.getLong("id");
            notificationData.setId(id);

            final String objectType = rs.getString("objectType");
            notificationData.setObjectType(objectType);

            final Long objectId = rs.getLong("objectId");
            notificationData.setObjectId(objectId);

            final Long actorId = rs.getLong("actor");
            notificationData.setActorId(actorId);

            final String action = rs.getString("action");
            notificationData.setAction(action);

            final String content = rs.getString("content");
            notificationData.setContent(content);

            final boolean isSystemGenerated = rs.getBoolean("isSystemGenerated");
            notificationData.setSystemGenerated(isSystemGenerated);

            final String createdAt = rs.getString("createdAt");
            notificationData.setCreatedAt(createdAt);

            return notificationData;
        }
    }
}

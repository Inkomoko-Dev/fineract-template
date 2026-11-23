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
package org.apache.fineract.portfolio.creditdecision.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tells the credit team when scoring an application has failed and will not be retried on its own. The team is whoever can
 * send a credit decision request: the users whose role holds SEND_CREDITDECISIONLOAN, or every function.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditDecisionAlertListener {

    static final String OBJECT_TYPE = "CREDIT_DECISION";
    static final String ACTION = "SCORING_FAILED";

    private final JdbcTemplate jdbc;
    private final NotificationWritePlatformService notifications;

    @EventListener
    public void onScoringFailed(final CreditDecisionScoringFailed event) {
        try {
            final List<Long> team = team();
            if (team.isEmpty()) {
                log.warn("Credit decision for application {} failed and nobody holds the permission to be told", event.getApplicationRef());
                return;
            }
            notifications.notify(team, OBJECT_TYPE, event.getLoanId(), ACTION, null, message(event), true);
        } catch (final RuntimeException e) {
            // an alert that cannot be delivered must never undo or hide the failure it is about
            log.warn("Could not alert the credit team about application {}", event.getApplicationRef(), e);
        }
    }

    static String message(final CreditDecisionScoringFailed event) {
        return "Credit scoring failed for application " + event.getApplicationRef() + " after " + event.getFailures() + " attempt"
                + (event.getFailures() == 1 ? "" : "s") + (event.isRetriesExhausted() ? " and will not be retried automatically" : "")
                + ": " + event.getMessage();
    }

    List<Long> team() {
        return jdbc.queryForList("SELECT DISTINCT u.id FROM m_appuser u JOIN m_appuser_role ur ON ur.appuser_id = u.id "
                + "JOIN m_role_permission rp ON rp.role_id = ur.role_id JOIN m_permission p ON p.id = rp.permission_id "
                + "WHERE u.is_deleted = FALSE AND u.enabled = TRUE AND p.code IN ('ALL_FUNCTIONS', 'SEND_CREDITDECISIONLOAN') "
                + "ORDER BY u.id", Long.class);
    }
}

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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.data;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user permissions in bulk reschedule operations. Indicates what actions the current user
 * is allowed to perform and which offices they have access to.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionsDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Whether the user can initiate bulk reschedule operations */
    private Boolean canInitiateBulkReschedule;

    /** Whether the user can approve bulk reschedule operations */
    private Boolean canApprove;

    /** List of office IDs accessible to the user */
    private List<Long> accessibleOffices;

    /** Whether the user can view the audit trail of bulk reschedule operations */
    private Boolean canViewAuditTrail;
}

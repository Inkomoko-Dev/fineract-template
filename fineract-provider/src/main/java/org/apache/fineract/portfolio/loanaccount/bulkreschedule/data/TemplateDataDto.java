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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for bulk reschedule template data. Provides dropdown options, validation rules, and user
 * permissions needed by the UI to render the bulk reschedule form.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateDataDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Filter options available for bulk reschedule criteria */
    private FilterOptionsDto filterOptions;

    /** Options for populating the reschedule details form (reasons, charge handling, carry-forward charges, strategies) */
    private RescheduleDetailOptionsDto rescheduleDetailOptions;

    /** Validation rules for the bulk reschedule operation */
    private ValidationRulesDto validationRules;

    /** User permissions for bulk reschedule operations */
    private UserPermissionsDto userPermissions;
}

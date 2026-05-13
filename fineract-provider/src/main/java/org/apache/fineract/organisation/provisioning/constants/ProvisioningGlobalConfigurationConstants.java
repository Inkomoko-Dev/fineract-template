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
package org.apache.fineract.organisation.provisioning.constants;

public final class ProvisioningGlobalConfigurationConstants {

    private ProvisioningGlobalConfigurationConstants() {}

    /** When enabled, skip strict contiguous bucket validation on save (overlap checks still apply). */
    public static final String RELAX_CONTIGUOUS_AGING_BANDS = "provisioning-relax-contiguous-aging-bands";

    /**
     * When enabled and {@code value} stores a {@code m_provision_category.id}, provisioning runs use that category's
     * criteria-definition row when loan DIA matches no configured range.
     */
    public static final String CATCH_ALL_CATEGORY_ID = "provisioning-catch-all-category-id";
}

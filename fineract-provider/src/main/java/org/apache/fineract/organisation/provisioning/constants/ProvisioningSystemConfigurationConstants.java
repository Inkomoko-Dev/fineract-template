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
public final class ProvisioningSystemConfigurationConstants {

    /** When enabled, provisioning criteria definitions need not cover day 0 with contiguous buckets. */
    public static final String RELAX_CONTIGUOUS_AGING_BANDS = "provisioning-relax-contiguous-aging-bands";

    /**
     * When enabled and no bucket matches DIA on a provisioning run, use the single open-ended definition
     * ({@code max_age} {@code NULL}) instead of failing; requires exactly one such bucket when this triggers.
     */
    public static final String FALLBACK_TO_OPEN_ENDED_ON_NO_MATCH = "provisioning-fallback-to-open-ended-on-no-match";

    private ProvisioningSystemConfigurationConstants() {}
}

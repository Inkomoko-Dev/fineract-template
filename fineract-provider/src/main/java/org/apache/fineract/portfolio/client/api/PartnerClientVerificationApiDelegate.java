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
package org.apache.fineract.portfolio.client.api;

import com.google.gson.JsonElement;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.PartnerClientVerificationRequest;
import org.apache.fineract.portfolio.client.data.PartnerClientVerificationResponse;
import org.apache.fineract.portfolio.client.service.PartnerClientVerificationService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Component;

@Component
public class PartnerClientVerificationApiDelegate {

    private final PlatformSecurityContext context;
    private final PartnerClientVerificationService verificationService;
    private final DefaultToApiJsonSerializer<PartnerClientVerificationResponse> toApiJsonSerializer;
    private final FromJsonHelper fromJsonHelper;

    public PartnerClientVerificationApiDelegate(final PlatformSecurityContext context,
            final PartnerClientVerificationService verificationService,
            final DefaultToApiJsonSerializer<PartnerClientVerificationResponse> toApiJsonSerializer, final FromJsonHelper fromJsonHelper) {
        this.context = context;
        this.verificationService = verificationService;
        this.toApiJsonSerializer = toApiJsonSerializer;
        this.fromJsonHelper = fromJsonHelper;
    }

    public String verify(final String apiRequestBodyAsJson) {
        final AppUser user = this.context.authenticatedUser();
        user.validateHasReadPermission("CLIENT");
        if (!user.hasAnyPermission("ALL_FUNCTIONS", PartnerClientApiConstants.VERIFY_PARTNER_CLIENT_PERMISSION)) {
            user.validateHasPermissionTo(PartnerClientApiConstants.VERIFY_PARTNER_CLIENT_PERMISSION);
        }

        if (StringUtils.isBlank(apiRequestBodyAsJson)) {
            throw new InvalidJsonException();
        }

        final JsonElement element = this.fromJsonHelper.parse(apiRequestBodyAsJson);
        final String nationalId = this.fromJsonHelper.extractStringNamed(PartnerClientApiConstants.VERIFICATION_NATIONAL_ID, element);
        final String phoneNumber = this.fromJsonHelper.extractStringNamed(PartnerClientApiConstants.VERIFICATION_PHONE_NUMBER, element);
        final String fullName = this.fromJsonHelper.extractStringNamed(PartnerClientApiConstants.VERIFICATION_FULL_NAME, element);
        final String sourceSystem = this.fromJsonHelper.extractStringNamed(PartnerClientApiConstants.VERIFICATION_SOURCE_SYSTEM, element);

        if (StringUtils.isBlank(nationalId) && StringUtils.isBlank(phoneNumber)) {
            throw new PlatformApiDataValidationException("validation.msg.partnerClient.verify.identifier.required",
                    "Either nationalId or phoneNumber must be provided.",
                    List.of(ApiParameterError.parameterError("validation.msg.partnerClient.verify.nationalId.required",
                            "nationalId is required when phoneNumber is not provided.", PartnerClientApiConstants.VERIFICATION_NATIONAL_ID,
                            nationalId)));
        }

        final PartnerClientVerificationRequest request = new PartnerClientVerificationRequest(nationalId, phoneNumber, fullName,
                sourceSystem);
        return this.toApiJsonSerializer.serialize(this.verificationService.verifyClient(request));
    }
}

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
package org.apache.fineract.commands.service;

import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Component;

/**
 * CGLT-690: amount-based senior checker gate for built-in maker-checker approve/reject of PARTIALWRITEOFF.
 * Threshold semantics match historical penalty waiver — strict {@code >}; disabled config row turns the gate off.
 */
@Component
@RequiredArgsConstructor
public class PartialWriteOffMakerCheckerPolicy {

    public static final String ACTION_PARTIAL_WRITE_OFF = "PARTIALWRITEOFF";
    public static final String SENIOR_CHECKER_PERMISSION = "PARTIALWRITEOFF_LOAN_SENIOR_CHECKER";

    private final ConfigurationDomainService configurationDomainService;

    public boolean isPartialWriteOffCommand(final CommandSource commandSource) {
        return commandSource != null && ACTION_PARTIAL_WRITE_OFF.equalsIgnoreCase(commandSource.getActionName());
    }

    /**
     * @return true when the amount is strictly above the configured threshold (null threshold = gate off).
     */
    public boolean requiresSeniorChecker(final BigDecimal writeOffAmount) {
        final Long amountThreshold = this.configurationDomainService.retrievePartialWriteOffApprovalAmountThreshold();
        return amountThreshold != null && writeOffAmount != null
                && writeOffAmount.compareTo(BigDecimal.valueOf(amountThreshold)) > 0;
    }

    public BigDecimal extractWriteOffAmount(final String commandAsJson, final FromJsonHelper fromApiJsonHelper) {
        if (StringUtils.isBlank(commandAsJson) || fromApiJsonHelper == null) {
            return BigDecimal.ZERO;
        }
        final JsonElement element = fromApiJsonHelper.parse(commandAsJson);
        final Locale locale = fromApiJsonHelper.extractLocaleParameter(element.getAsJsonObject());
        BigDecimal total = BigDecimal.ZERO;
        total = total.add(nullToZero(fromApiJsonHelper.extractBigDecimalNamed("principalPortion", element, locale)));
        total = total.add(nullToZero(fromApiJsonHelper.extractBigDecimalNamed("interestPortion", element, locale)));
        total = total.add(nullToZero(fromApiJsonHelper.extractBigDecimalNamed("feeChargesPortion", element, locale)));
        total = total.add(nullToZero(fromApiJsonHelper.extractBigDecimalNamed("penaltyChargesPortion", element, locale)));
        return total;
    }

    public void validateSeniorCheckerIfRequired(final CommandSource commandSource, final AppUser checker,
            final FromJsonHelper fromApiJsonHelper) {
        if (!isPartialWriteOffCommand(commandSource)) {
            return;
        }
        final BigDecimal amount = extractWriteOffAmount(commandSource.json(), fromApiJsonHelper);
        if (!requiresSeniorChecker(amount)) {
            return;
        }
        if (checker.hasNotPermissionForAnyOf("CHECKER_SUPER_USER", SENIOR_CHECKER_PERMISSION)) {
            throw new NoAuthorizationException(
                    "User has no authority to be a senior checker for high-value partial write-off: " + SENIOR_CHECKER_PERMISSION);
        }
    }

    private static BigDecimal nullToZero(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

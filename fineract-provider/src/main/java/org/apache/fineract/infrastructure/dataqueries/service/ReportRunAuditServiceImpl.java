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
package org.apache.fineract.infrastructure.dataqueries.service;

import com.google.gson.JsonObject;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.ws.rs.core.MultivaluedMap;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.infrastructure.core.api.ApiParameterHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportRunAuditServiceImpl implements ReportRunAuditService {

    public static final String ENTITY_NAME = "REPORT";
    public static final String ACTION_RUN = "RUN";
    public static final String ACTION_EXPORT = "EXPORT";
    private static final String HREF_PREFIX = "runreports/";
    private static final int HREF_MAX_LENGTH = 100;
    private static final String OUTPUT_TYPE_PARAMETER = "output-type";
    private static final String TENANT_PARAMETER = "tenantIdentifier";

    private final PlatformSecurityContext context;
    private final CommandSourceRepository commandSourceRepository;

    @Override
    @Transactional
    public void recordReportRun(final String reportName, final MultivaluedMap<String, String> queryParams) {
        if (!isAuditable(queryParams)) {
            return;
        }
        final AppUser user = this.context.authenticatedUser();
        final String format = exportFormat(queryParams);
        final CommandSource entry = CommandSource.reportRunEntry(format == null ? ACTION_RUN : ACTION_EXPORT, ENTITY_NAME,
                StringUtils.left(HREF_PREFIX + reportName, HREF_MAX_LENGTH), toJson(reportName, format, queryParams), user,
                ZonedDateTime.now(DateUtils.getDateTimeZoneOfTenant()), format == null ? reportName : reportName + " (" + format + ")");
        entry.updateForAudit(user.getOffice() == null ? null : user.getOffice().getId(), null, null, null, null, null, null);
        this.commandSourceRepository.saveAndFlush(entry);
    }

    static boolean isAuditable(final MultivaluedMap<String, String> queryParams) {
        if (ApiParameterHelper.parameterType(queryParams)) {
            return false;
        }
        final Integer offset = integerParameter(queryParams, "offset");
        if (offset != null && offset > 0) {
            return false;
        }
        final Integer limit = integerParameter(queryParams, "limit");
        final boolean countProbe = limit != null && limit == 1 && !"false".equalsIgnoreCase(queryParams.getFirst("includeCount"));
        return !countProbe;
    }

    static String exportFormat(final MultivaluedMap<String, String> queryParams) {
        if (ApiParameterHelper.exportPdf(queryParams)) {
            return "PDF";
        }
        if (ApiParameterHelper.exportXLSX(queryParams)) {
            return "XLSX";
        }
        if (ApiParameterHelper.exportAPI(queryParams)) {
            return "API";
        }
        if (ApiParameterHelper.exportCsv(queryParams)) {
            return "CSV";
        }
        final String outputType = StringUtils.trimToNull(queryParams.getFirst(OUTPUT_TYPE_PARAMETER));
        if (outputType != null && !"HTML".equalsIgnoreCase(outputType)) {
            return outputType.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private static String toJson(final String reportName, final String format, final MultivaluedMap<String, String> queryParams) {
        final JsonObject json = new JsonObject();
        json.addProperty("reportName", reportName);
        if (format != null) {
            json.addProperty("format", format);
        }
        final JsonObject parameters = new JsonObject();
        final Map<String, List<String>> sorted = new TreeMap<>(queryParams);
        sorted.remove(TENANT_PARAMETER);
        sorted.forEach((name, values) -> parameters.addProperty(name, values == null ? null : String.join(",", values)));
        json.add("parameters", parameters);
        return json.toString();
    }

    private static Integer integerParameter(final MultivaluedMap<String, String> queryParams, final String name) {
        final String value = StringUtils.trimToNull(queryParams.getFirst(name));
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

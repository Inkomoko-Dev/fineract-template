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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.fineract.commands.domain.CommandProcessingResultType;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class ReportRunAuditServiceImplTest {

    private static final String REPORT = "Interest and Penalty Receivable - Loan Summary";

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private CommandSourceRepository commandSourceRepository;

    @Mock
    private AppUser user;

    @Mock
    private Office office;

    private ReportRunAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        this.service = new ReportRunAuditServiceImpl(this.context, this.commandSourceRepository);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.clearTenant();
    }

    private void givenUserInOffice(final long officeId) {
        when(this.context.authenticatedUser()).thenReturn(this.user);
        when(this.user.getOffice()).thenReturn(this.office);
        when(this.office.getId()).thenReturn(officeId);
    }

    private static MultivaluedMap<String, String> params(final String... pairs) {
        final MultivaluedMap<String, String> params = new MultivaluedStringMap();
        for (int i = 0; i < pairs.length; i += 2) {
            params.add(pairs[i], pairs[i + 1]);
        }
        return params;
    }

    private CommandSource recorded() {
        final ArgumentCaptor<CommandSource> captor = ArgumentCaptor.forClass(CommandSource.class);
        verify(this.commandSourceRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void runIsRecordedWithUserOfficeAndEveryParameter() {
        givenUserInOffice(3L);

        this.service.recordReportRun(REPORT, params("R_asOn", "2026-10-02", "R_officeId", "1", "R_filterLoanId", "413551",
                "tenantIdentifier", "default", "limit", "20", "offset", "0", "includeCount", "false"));

        final CommandSource entry = recorded();
        assertThat(entry.getActionName()).isEqualTo("RUN");
        assertThat(entry.getEntityName()).isEqualTo("REPORT");
        assertThat(entry.getResourceGetUrl()).isEqualTo("runreports/" + REPORT);
        assertThat(entry.getNotes()).isEqualTo(REPORT);
        assertThat(entry.getMaker()).isSameAs(this.user);
        assertThat(entry.getOfficeId()).isEqualTo(3L);
        assertThat(entry.getMadeOnDate()).isNotNull();
        final JsonObject json = JsonParser.parseString(entry.getCommandAsJson()).getAsJsonObject();
        assertThat(json.get("reportName").getAsString()).isEqualTo(REPORT);
        assertThat(json.has("format")).isFalse();
        final JsonObject parameters = json.getAsJsonObject("parameters");
        assertThat(parameters.get("R_asOn").getAsString()).isEqualTo("2026-10-02");
        assertThat(parameters.get("R_officeId").getAsString()).isEqualTo("1");
        assertThat(parameters.get("R_filterLoanId").getAsString()).isEqualTo("413551");
        assertThat(parameters.has("tenantIdentifier")).isFalse();
    }

    @Test
    void csvExportIsRecordedAsExport() {
        givenUserInOffice(1L);

        this.service.recordReportRun(REPORT, params("R_asOn", "2026-10-02", "exportCSV", "true"));

        final CommandSource entry = recorded();
        assertThat(entry.getActionName()).isEqualTo("EXPORT");
        assertThat(entry.getNotes()).isEqualTo(REPORT + " (CSV)");
        assertThat(JsonParser.parseString(entry.getCommandAsJson()).getAsJsonObject().get("format").getAsString()).isEqualTo("CSV");
    }

    @Test
    void excelExportIsRecordedAsExport() {
        givenUserInOffice(1L);

        this.service.recordReportRun(REPORT, params("exportXLSX", "true"));

        assertThat(JsonParser.parseString(recorded().getCommandAsJson()).getAsJsonObject().get("format").getAsString()).isEqualTo("XLSX");
    }

    @Test
    void outputTypeDecidesPentahoFormat() {
        assertThat(ReportRunAuditServiceImpl.exportFormat(params("output-type", "PDF"))).isEqualTo("PDF");
        assertThat(ReportRunAuditServiceImpl.exportFormat(params("output-type", "xls"))).isEqualTo("XLS");
        assertThat(ReportRunAuditServiceImpl.exportFormat(params("output-type", "HTML"))).isNull();
        assertThat(ReportRunAuditServiceImpl.exportFormat(params("exportPDF", "true"))).isEqualTo("PDF");
        assertThat(ReportRunAuditServiceImpl.exportFormat(params("exportAPI", "true"))).isEqualTo("API");
        assertThat(ReportRunAuditServiceImpl.exportFormat(params("exportCSV", "false"))).isNull();
    }

    @Test
    void parameterLookupsAreNotRecorded() {
        this.service.recordReportRun("FullParameterList", params("parameterType", "true", "R_reportListing", "'" + REPORT + "'"));

        verify(this.commandSourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void laterPagesOfTheSameRunAreNotRecorded() {
        this.service.recordReportRun(REPORT, params("limit", "20", "offset", "40", "includeCount", "false"));

        verify(this.commandSourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void backgroundRowCountProbeIsNotRecorded() {
        this.service.recordReportRun(REPORT, params("limit", "1", "offset", "0"));

        verify(this.commandSourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void singleRowPageWithoutCountIsRecorded() {
        assertThat(ReportRunAuditServiceImpl.isAuditable(params("limit", "1", "offset", "0", "includeCount", "false"))).isTrue();
        assertThat(ReportRunAuditServiceImpl.isAuditable(params())).isTrue();
        assertThat(ReportRunAuditServiceImpl.isAuditable(params("offset", "abc"))).isTrue();
    }

    @Test
    void deniedAttemptIsRecordedAsRejected() {
        givenUserInOffice(4L);

        this.service.recordDeniedReportRun(REPORT, params("R_asOn", "2026-10-02", "exportCSV", "true"));

        final CommandSource entry = recorded();
        assertThat(entry.getActionName()).isEqualTo("EXPORT");
        assertThat(entry.getProcessingResult()).isEqualTo(CommandProcessingResultType.REJECTED.getValue());
        assertThat(entry.getNotes()).isEqualTo(REPORT + " (CSV) - not authorised");
        assertThat(entry.getOfficeId()).isEqualTo(4L);
        assertThat(JsonParser.parseString(entry.getCommandAsJson()).getAsJsonObject().getAsJsonObject("parameters").get("R_asOn")
                .getAsString()).isEqualTo("2026-10-02");
    }

    @Test
    void allowedRunIsRecordedAsProcessed() {
        givenUserInOffice(1L);

        this.service.recordReportRun(REPORT, params());

        assertThat(recorded().getProcessingResult()).isEqualTo(CommandProcessingResultType.PROCESSED.getValue());
    }

    @Test
    void deniedCountProbeIsNotRecordedTwice() {
        this.service.recordDeniedReportRun(REPORT, params("limit", "1", "offset", "0"));

        verify(this.commandSourceRepository, never()).saveAndFlush(any());
    }

    @Test
    void failureToRecordStopsTheRun() {
        givenUserInOffice(1L);
        when(this.commandSourceRepository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> this.service.recordReportRun(REPORT, params("exportCSV", "true")))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}

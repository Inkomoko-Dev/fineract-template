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
package org.apache.fineract.infrastructure.dataqueries.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.fineract.infrastructure.dataqueries.service.ReadReportingService;
import org.apache.fineract.infrastructure.dataqueries.service.ReportRunAuditService;
import org.apache.fineract.infrastructure.report.provider.ReportingProcessServiceProvider;
import org.apache.fineract.infrastructure.report.service.ReportingProcessService;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RunreportsApiResourceTest {

    private static final String REPORT = "Interest and Penalty Received - Transactions";

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private ReadReportingService readReportingService;

    @Mock
    private ReportingProcessServiceProvider provider;

    @Mock
    private ReportingProcessService processService;

    @Mock
    private ReportRunAuditService auditService;

    @Mock
    private UriInfo uriInfo;

    @Mock
    private AppUser user;

    private RunreportsApiResource resource;

    @BeforeEach
    void setUp() {
        this.resource = new RunreportsApiResource(this.context, this.readReportingService, this.provider, this.auditService);
        final MultivaluedMap<String, String> params = new MultivaluedStringMap();
        params.add("R_startDate", "2026-10-01");
        params.add("exportCSV", "true");
        when(this.uriInfo.getQueryParameters()).thenReturn(params);
    }

    private void givenPermittedReport() {
        when(this.context.authenticatedUser()).thenReturn(this.user);
        when(this.user.hasNotPermissionForReport(REPORT)).thenReturn(false);
        when(this.readReportingService.getReportType(eq(REPORT), anyBoolean(), anyBoolean())).thenReturn("Table");
        when(this.provider.findReportingProcessService("Table")).thenReturn(this.processService);
    }

    @Test
    void runIsAuditedBeforeTheReportExecutes() {
        givenPermittedReport();
        when(this.processService.processRequest(eq(REPORT), any())).thenReturn(Response.ok().build());

        this.resource.runReport(REPORT, this.uriInfo, false);

        final InOrder order = inOrder(this.auditService, this.processService);
        order.verify(this.auditService).recordReportRun(eq(REPORT), any());
        order.verify(this.processService).processRequest(eq(REPORT), any());
    }

    @Test
    void reportDoesNotExecuteWhenTheAuditCannotBeWritten() {
        givenPermittedReport();
        doThrow(new IllegalStateException("audit down")).when(this.auditService).recordReportRun(eq(REPORT), any());

        assertThatThrownBy(() -> this.resource.runReport(REPORT, this.uriInfo, false)).isInstanceOf(IllegalStateException.class);
        verify(this.processService, never()).processRequest(any(), any());
    }

    @Test
    void unauthorisedRunIsRejectedWithoutAnAuditRow() {
        when(this.context.authenticatedUser()).thenReturn(this.user);
        when(this.user.hasNotPermissionForReport(REPORT)).thenReturn(true);

        assertThatThrownBy(() -> this.resource.runReport(REPORT, this.uriInfo, false)).isInstanceOf(NoAuthorizationException.class);
        verifyNoInteractions(this.auditService);
    }
}

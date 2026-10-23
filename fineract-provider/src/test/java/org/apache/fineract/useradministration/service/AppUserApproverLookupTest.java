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
package org.apache.fineract.useradministration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.data.OfficeData;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.organisation.staff.exception.StaffNotFoundException;
import org.apache.fineract.organisation.staff.service.StaffReadPlatformService;
import org.apache.fineract.useradministration.data.AppUserData;
import org.apache.fineract.useradministration.data.RoleData;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class AppUserApproverLookupTest {

    private static final Long LOAN_OFFICE_ID = 120L;
    private static final String LOAN_OFFICE_HIERARCHY = ".121.120.";
    private static final String PERMISSION = "APPROVE_HISTORICALPENALTYWAIVER";
    private static final Long HEAD_OFFICE_APPROVER_ID = 296L;
    private static final Long HEAD_OFFICE_STAFF_ID = 2L;

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final OfficeReadPlatformService officeReadPlatformService = mock(OfficeReadPlatformService.class);
    private final RoleReadPlatformService roleReadPlatformService = mock(RoleReadPlatformService.class);
    private final StaffReadPlatformService staffReadPlatformService = mock(StaffReadPlatformService.class);

    @Test
    public void listsAHeadOfficeApproverWhoseStaffTheCallerCannotSee() throws SQLException {
        final OfficeData loanOffice = mock(OfficeData.class);
        when(loanOffice.getHierarchy()).thenReturn(LOAN_OFFICE_HIERARCHY);
        when(this.officeReadPlatformService.retrieveOffice(LOAN_OFFICE_ID)).thenReturn(loanOffice);
        when(this.staffReadPlatformService.retrieveStaff(anyLong())).thenThrow(new StaffNotFoundException(HEAD_OFFICE_STAFF_ID));
        final List<RoleData> roles = new ArrayList<>();
        when(this.roleReadPlatformService.retrieveAppUserRoles(HEAD_OFFICE_APPROVER_ID)).thenReturn(roles);

        final ResultSet row = headOfficeApproverLinkedToStaff();
        when(this.jdbcTemplate.query(anyString(), any(RowMapper.class), eq(LOAN_OFFICE_HIERARCHY), eq(PERMISSION)))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(1)).mapRow(row, 0)));

        final Collection<AppUserData> approvers = service().retrieveUsersByOfficeAndPermission(LOAN_OFFICE_ID, PERMISSION);

        assertEquals(1, approvers.size());
        assertTrue(approvers.iterator().next().hasIdentifyOf(HEAD_OFFICE_APPROVER_ID));
        verify(this.roleReadPlatformService).retrieveAppUserRoles(HEAD_OFFICE_APPROVER_ID);
        verify(this.staffReadPlatformService, never()).retrieveStaff(anyLong());
    }

    private AppUserReadPlatformServiceImpl service() {
        return new AppUserReadPlatformServiceImpl(mock(PlatformSecurityContext.class), this.jdbcTemplate, this.officeReadPlatformService,
                this.roleReadPlatformService, mock(AppUserRepository.class), this.staffReadPlatformService);
    }

    private ResultSet headOfficeApproverLinkedToStaff() throws SQLException {
        final ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(HEAD_OFFICE_APPROVER_ID);
        when(row.getString("username")).thenReturn("Automation01");
        when(row.getString("firstname")).thenReturn("Automation01");
        when(row.getString("lastname")).thenReturn("Automation01");
        when(row.getString("officeName")).thenReturn("Inkomoko - USA");
        when(row.findColumn("officeId")).thenReturn(7);
        when(row.getLong(7)).thenReturn(1L);
        when(row.findColumn("staffId")).thenReturn(9);
        when(row.getLong(9)).thenReturn(HEAD_OFFICE_STAFF_ID);
        return row;
    }
}

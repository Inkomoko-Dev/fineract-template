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
package org.apache.fineract.accounting.provisioning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class ProvisioningEntriesReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PaginationHelper loanProductProvisioningEntryDataPaginationHelper;

    @Mock
    private PaginationHelper provisioningEntryDataPaginationHelper;

    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;

    private ProvisioningEntriesReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        this.service = new ProvisioningEntriesReadPlatformServiceImpl(this.jdbcTemplate,
                this.loanProductProvisioningEntryDataPaginationHelper, this.provisioningEntryDataPaginationHelper, this.sqlGenerator);
    }

    @Test
    void cutoffDateBindValues_fillsOneArgumentPerPlaceholder() {
        Object[] args = ProvisioningEntriesReadPlatformServiceImpl.cutoffDateBindValues("30 September 2026", "DATE(?) AND DATE(?)");
        assertEquals(2, args.length);
        assertEquals("30 September 2026", args[0]);
        assertEquals("30 September 2026", args[1]);
    }

    @Test
    void candidateQuery_hasCommaAfterOutstandingBalanceAndBindsEveryPlaceholder() {
        String sql = new ProvisioningEntriesReadPlatformServiceImpl.LoanProductProvisioningEntryMapper(this.sqlGenerator).schema();
        assertTrue(sql.contains("AS outstandingbalance,"));
        assertEquals(10, ProvisioningEntriesReadPlatformServiceImpl.placeholderCount(sql));

        when(this.jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            Object[] invocationArgs = invocation.getArguments();
            assertEquals(sql, invocationArgs[0]);
            Object[] binds = invocationArgs.length == 3 && invocationArgs[2] instanceof Object[]
                    ? (Object[]) invocationArgs[2]
                    : Arrays.copyOfRange(invocationArgs, 2, invocationArgs.length);
            assertEquals(10, binds.length);
            for (Object bind : binds) {
                assertEquals("2026-09-30", bind);
            }
            return Collections.emptyList();
        });

        this.service.retrieveLoanProductsProvisioningData(LocalDate.of(2026, 9, 30));

        verify(this.jdbcTemplate).query(eq(sql), any(RowMapper.class), any(Object[].class));
    }
}

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
package org.apache.fineract.portfolio.collateral.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.ResultSet;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.collateral.data.CollateralData;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollateralReadPlatformServiceImplTest {

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private LoanRepositoryWrapper loanRepositoryWrapper;

    private CollateralReadPlatformServiceImpl readPlatformService;

    @BeforeEach
    void setUp() {
        readPlatformService = new CollateralReadPlatformServiceImpl(context, jdbcTemplate, loanRepositoryWrapper);
    }

    @Test
    void retrieveCollateralSqlUsesLoanCurrencyWithLeftJoins() {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(10L), eq(158L))).thenReturn(null);

        readPlatformService.retrieveCollateral(10L, 158L);

        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sqlCaptor.capture(), any(RowMapper.class), eq(10L), eq(158L));
        final String sql = sqlCaptor.getValue();

        assertTrue(sql.contains("loan.currency_code as currencyCode"));
        assertTrue(sql.contains("LEFT JOIN m_organisation_currency oc"));
        assertTrue(sql.contains("LEFT JOIN m_currency cur"));
        assertFalse(sql.contains(" JOIN m_organisation_currency ") && !sql.contains("LEFT JOIN m_organisation_currency"));
    }

    @Test
    void retrieveCollateralKeepsLoanCurrencyWhenOrganisationCurrencyMissing() throws Exception {
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(10L), eq(158L))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            final RowMapper<CollateralData> mapper = invocation.getArgument(1);
            final ResultSet rs = mock(ResultSet.class);
            when(rs.findColumn("currencyDecimalPlaces")).thenReturn(2);
            when(rs.findColumn("inMultiplesOf")).thenReturn(3);
            when(rs.getObject(2)).thenReturn(2);
            when(rs.getObject(3)).thenReturn(null);
            when(rs.getLong("id")).thenReturn(158L);
            when(rs.getString("description")).thenReturn("Toyota Hilux");
            when(rs.getLong("typeId")).thenReturn(1L);
            when(rs.getBigDecimal("value")).thenReturn(new BigDecimal("2800000"));
            when(rs.getString("typeName")).thenReturn("Motor Vehicle");
            when(rs.getString("currencyCode")).thenReturn("KES");
            when(rs.getString("currencyName")).thenReturn("Kenyan Shilling");
            when(rs.getString("currencyNameCode")).thenReturn("currency.KES");
            when(rs.getString("currencyDisplaySymbol")).thenReturn("KSh");
            return mapper.mapRow(rs, 0);
        });

        final CollateralData collateral = readPlatformService.retrieveCollateral(10L, 158L);

        final CurrencyData currency = currencyOf(collateral);
        assertEquals("KES", currency.code());
        assertEquals("Kenyan Shilling", currency.getName());
        assertNotEquals("", currency.code());
        assertNotEquals(CurrencyData.blank().code(), currency.code());
    }

    private static CurrencyData currencyOf(final CollateralData collateral) throws Exception {
        final Field currencyField = CollateralData.class.getDeclaredField("currency");
        currencyField.setAccessible(true);
        return (CurrencyData) currencyField.get(collateral);
    }
}

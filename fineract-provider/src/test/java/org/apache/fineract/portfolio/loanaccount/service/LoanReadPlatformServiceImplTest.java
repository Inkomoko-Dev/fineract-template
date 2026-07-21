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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.loanaccount.data.DisbursementData;
import org.apache.fineract.portfolio.loanaccount.data.RepaymentScheduleRelatedLoanData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanScheduleData;
import org.apache.fineract.portfolio.loanaccount.loanschedule.data.LoanSchedulePeriodData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

class LoanReadPlatformServiceImplTest {

    private static final LocalDate DISBURSEMENT_DATE = LocalDate.of(2026, 6, 8);

    private RoundingMode originalRoundingMode;
    private MathContext originalMathContext;

    @BeforeEach
    void setUp() {
        this.originalRoundingMode = (RoundingMode) ReflectionTestUtils.getField(MoneyHelper.class, "roundingMode");
        this.originalMathContext = (MathContext) ReflectionTestUtils.getField(MoneyHelper.class, "mathContext");
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", RoundingMode.HALF_EVEN);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", new MathContext(12, RoundingMode.HALF_EVEN));
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Africa/Nairobi", null));
        ThreadLocalContextUtil
                .setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 6, 8))));
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(MoneyHelper.class, "roundingMode", this.originalRoundingMode);
        ReflectionTestUtils.setField(MoneyHelper.class, "mathContext", this.originalMathContext);
    }

    @Test
    void repaymentScheduleUsesActualDisbursementFeePaymentStateAfterInsuranceAdjustment() throws Exception {
        final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        when(context.authenticatedUser()).thenReturn(null);
        stubDisbursementChargeAmounts(jdbcTemplate);
        stubRepaymentSchedule(jdbcTemplate);

        final LoanReadPlatformServiceImpl service = new LoanReadPlatformServiceImpl(context, null, null, null, null, null,
                null, null, null, jdbcTemplate, null, null, null, null, null, null, null, null, null, null, null, null,
                null, mock(DatabaseSpecificSQLGenerator.class), null, null, null, null, null, null, null, null, null,null,null,null,null);

        final CurrencyData currency = new CurrencyData("KES", 2, 0);
        final RepaymentScheduleRelatedLoanData relatedData = new RepaymentScheduleRelatedLoanData(DISBURSEMENT_DATE,
                DISBURSEMENT_DATE, currency, new BigDecimal("5000.00"), BigDecimal.ZERO, new BigDecimal("1200.00"));
        final Collection<DisbursementData> disbursements = List.of(new DisbursementData(525L, DISBURSEMENT_DATE,
                DISBURSEMENT_DATE, new BigDecimal("5000.00"), new BigDecimal("4200.00"), null, null, null));

        final LoanScheduleData schedule = service.retrieveRepaymentSchedule(427367L, relatedData, disbursements, false,
                new BigDecimal("800.00"));

        final List<LoanSchedulePeriodData> periods = new ArrayList<>(schedule.getPeriods());
        final LoanSchedulePeriodData disbursementPeriod = periods.get(0);
        assertAmount("1200.00", disbursementPeriod.feeChargesDue());
        assertAmount("800.00", disbursementPeriod.feeChargesPaid());
        assertAmount("400.00", disbursementPeriod.feeChargesOutstanding());
        assertAmount("400.00", disbursementPeriod.totalDueForPeriod());
        assertAmount("800.00", disbursementPeriod.totalPaidForPeriod());
        assertAmount("400.00", disbursementPeriod.totalOutstandingForPeriod());
        assertAmount("5650.00", (BigDecimal) ReflectionTestUtils.getField(schedule, "totalRepaymentExpected"));
        assertAmount("800.00", (BigDecimal) ReflectionTestUtils.getField(schedule, "totalRepayment"));
        assertAmount("5650.00", (BigDecimal) ReflectionTestUtils.getField(schedule, "totalOutstanding"));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void stubDisbursementChargeAmounts(final JdbcTemplate jdbcTemplate) throws Exception {
        final ResultSet chargeResultSet = mock(ResultSet.class);
        when(chargeResultSet.getInt("chargeCount")).thenReturn(1);
        when(chargeResultSet.getBigDecimal("amount")).thenReturn(new BigDecimal("1200.00"));
        when(chargeResultSet.getBigDecimal("amountPaid")).thenReturn(new BigDecimal("800.00"));
        when(chargeResultSet.getBigDecimal("amountWaived")).thenReturn(BigDecimal.ZERO);
        when(chargeResultSet.getBigDecimal("amountWrittenOff")).thenReturn(BigDecimal.ZERO);
        when(chargeResultSet.getBigDecimal("amountOutstanding")).thenReturn(new BigDecimal("400.00"));
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(), any(), any())).thenAnswer(invocation -> {
            final RowMapper rowMapper = invocation.getArgument(1);
            return rowMapper.mapRow(chargeResultSet, 0);
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void stubRepaymentSchedule(final JdbcTemplate jdbcTemplate) throws Exception {
        final ResultSet scheduleResultSet = repaymentScheduleResultSet();
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), any())).thenAnswer(invocation -> {
            final ResultSetExtractor extractor = invocation.getArgument(1);
            return extractor.extractData(scheduleResultSet);
        });
    }

    private ResultSet repaymentScheduleResultSet() throws Exception {
        final ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getLong("loanId")).thenReturn(427367L);
        when(resultSet.getBoolean("complete")).thenReturn(false);
        when(resultSet.getDate("fromDate")).thenReturn(Date.valueOf(DISBURSEMENT_DATE));
        when(resultSet.getDate("dueDate")).thenReturn(Date.valueOf(LocalDate.of(2026, 12, 8)));
        when(resultSet.getDate("obligationsMetOnDate")).thenReturn(null);
        when(resultSet.getBigDecimal("principalDue")).thenReturn(new BigDecimal("5000.00"));
        when(resultSet.getBigDecimal("principalPaid")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("principalWrittenOff")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("interestDue")).thenReturn(new BigDecimal("250.00"));
        when(resultSet.getBigDecimal("interestPaid")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("interestWaived")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("interestWrittenOff")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("feeChargesDue")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("feeChargesPaid")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("feeChargesWaived")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("feeChargesWrittenOff")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("penaltyChargesDue")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("penaltyChargesPaid")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("penaltyChargesWaived")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("penaltyChargesWrittenOff")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("totalPaidInAdvanceForPeriod")).thenReturn(BigDecimal.ZERO);
        when(resultSet.getBigDecimal("totalPaidLateForPeriod")).thenReturn(BigDecimal.ZERO);
        stubColumn(resultSet, "period", 1);
        return resultSet;
    }

    private void stubColumn(final ResultSet resultSet, final String columnName, final Object value) throws Exception {
        when(resultSet.findColumn(columnName)).thenReturn(1);
        when(resultSet.getObject(1)).thenReturn(value);
    }

    private void assertAmount(final String expected, final BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    @Test
    void repaymentTransactionTemplateSchemaAllowsFullyCompletedSchedules() throws Exception {
        final DatabaseSpecificSQLGenerator sqlGenerator = mock(DatabaseSpecificSQLGenerator.class);
        when(sqlGenerator.escape("name")).thenReturn("`name`");
        when(sqlGenerator.escape("code")).thenReturn("`code`");

        final Class<?> mapperClass = Class.forName(
                "org.apache.fineract.portfolio.loanaccount.service.LoanReadPlatformServiceImpl$RepaymentTransactionTemplateMapper");
        final var constructor = mapperClass.getDeclaredConstructor(DatabaseSpecificSQLGenerator.class);
        constructor.setAccessible(true);
        final Object repaymentMapper = constructor.newInstance(sqlGenerator);
        final String schema = (String) ReflectionTestUtils.invokeMethod(repaymentMapper, "schema");

        // Old SQL required incomplete rows and broke overpaid / fully-settled schedules (CGLT-652 / FINERACT-2421).
        org.junit.jupiter.api.Assertions.assertFalse(schema.contains("completed_derived = false"));
        org.junit.jupiter.api.Assertions.assertTrue(schema.contains("ORDER BY ls.completed_derived ASC"));
        org.junit.jupiter.api.Assertions.assertTrue(schema.toUpperCase().contains("LIMIT 1"));
        org.junit.jupiter.api.Assertions.assertTrue(schema.contains("LEFT JOIN"));
    }
}

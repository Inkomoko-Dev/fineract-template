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
package org.apache.fineract.portfolio.client.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import java.time.LocalDate;
import java.util.HashMap;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.office.domain.Office;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

public class ClientMigratedActivationDateTest {

    private static final String OFFICE_OPENING_DATE_ERROR = "error.msg.clients.activationDate.cannot.be.before.office.activation.date";

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 9, 1);
    private static final LocalDate OFFICE_OPENING_DATE = LocalDate.of(2020, 1, 1);
    private static final LocalDate ACTIVATION_BEFORE_OFFICE_OPENED = LocalDate.of(2015, 6, 30);

    @BeforeEach
    public void setUp() {
        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE);
        businessDates.put(BusinessDateType.COB_DATE, BUSINESS_DATE.minusDays(1));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    public void nonMigratedClientIsRejectedWhenActivatedBeforeTheOfficeOpened() {
        final Client client = pendingClient(false);

        assertThatThrownBy(() -> client.activate(null, DateUtils.getDefaultFormatter(), ACTIVATION_BEFORE_OFFICE_OPENED))
                .isInstanceOfSatisfying(PlatformApiDataValidationException.class,
                        e -> assertThat(errorCodes(e)).contains(OFFICE_OPENING_DATE_ERROR));
    }

    @Test
    public void migratedClientIsAcceptedWhenActivatedBeforeTheOfficeOpened() {
        final Client client = pendingClient(true);

        assertThatCode(() -> client.activate(null, DateUtils.getDefaultFormatter(), ACTIVATION_BEFORE_OFFICE_OPENED))
                .doesNotThrowAnyException();

        assertThat(client.getActivationLocalDate()).isEqualTo(ACTIVATION_BEFORE_OFFICE_OPENED);
        assertThat(client.isActive()).isTrue();
    }

    @Test
    public void migratedClientIsStillRejectedWhenActivationDateIsInTheFuture() {
        final Client client = pendingClient(true);
        final LocalDate futureDate = BUSINESS_DATE.plusDays(1);

        assertThatThrownBy(() -> client.activate(null, DateUtils.getDefaultFormatter(), futureDate))
                .isInstanceOfSatisfying(PlatformApiDataValidationException.class,
                        e -> assertThat(errorCodes(e)).contains("error.msg.clients.activationDate.in.the.future")
                                .doesNotContain(OFFICE_OPENING_DATE_ERROR));
    }

    @Test
    public void migratedClientIsStillRejectedWhenSubmittedOnDateIsAfterTheActivationDate() {
        final Client client = pendingClient(true);
        ReflectionTestUtils.setField(client, "submittedOnDate", ACTIVATION_BEFORE_OFFICE_OPENED.plusDays(1));

        assertThatThrownBy(() -> client.activate(null, DateUtils.getDefaultFormatter(), ACTIVATION_BEFORE_OFFICE_OPENED))
                .isInstanceOfSatisfying(PlatformApiDataValidationException.class,
                        e -> assertThat(errorCodes(e)).contains("error.msg.clients.submittedOnDate.after.activation.date")
                                .doesNotContain(OFFICE_OPENING_DATE_ERROR));
    }

    @Test
    public void migrationTemplateCanCreateAMigratedClientActivatedBeforeTheOfficeOpened() {
        assertThatCode(() -> createClientFromImportPayload(true)).doesNotThrowAnyException();
    }

    @Test
    public void migrationTemplateStillRejectsANonMigratedClientActivatedBeforeTheOfficeOpened() {
        assertThatThrownBy(() -> createClientFromImportPayload(false))
                .isInstanceOfSatisfying(PlatformApiDataValidationException.class,
                        e -> assertThat(errorCodes(e)).contains(OFFICE_OPENING_DATE_ERROR));
    }

    @Test
    public void migratedDefaultsToFalse() {
        assertThat(new Client().isMigrated()).isFalse();
    }

    private Client pendingClient(final boolean migrated) {
        final Office office = Office.headOffice("Kenya Capital", OFFICE_OPENING_DATE, null);

        final Client client = new Client();
        ReflectionTestUtils.setField(client, "office", office);
        ReflectionTestUtils.setField(client, "status", ClientStatus.PENDING.getValue());
        ReflectionTestUtils.setField(client, "firstname", "Wanjiku");
        ReflectionTestUtils.setField(client, "lastname", "Kamau");
        ReflectionTestUtils.setField(client, "submittedOnDate", ACTIVATION_BEFORE_OFFICE_OPENED);
        ReflectionTestUtils.setField(client, "migrated", migrated);
        return client;
    }

    private Client createClientFromImportPayload(final boolean migrated) {
        final Office office = Office.headOffice("Kenya Capital", OFFICE_OPENING_DATE, null);
        final String json = String.format(
                "{\"officeId\":120,\"firstname\":\"Wanjiku\",\"lastname\":\"Kamau\",\"active\":true,"
                        + "\"activationDate\":\"%s\",\"submittedOnDate\":\"%s\",\"locale\":\"en\","
                        + "\"dateFormat\":\"yyyy-MM-dd\",\"migrated\":%s}",
                ACTIVATION_BEFORE_OFFICE_OPENED, ACTIVATION_BEFORE_OFFICE_OPENED, migrated);

        final FromJsonHelper helper = new FromJsonHelper();
        final JsonElement parsed = helper.parse(json);
        final JsonCommand command = JsonCommand.from(json, parsed, helper, null, null, null, null, null, null, null, null, null, null,
                null, null);

        return Client.createNew(null, office, null, null, null, null, null, null, 1, command);
    }

    private static java.util.List<String> errorCodes(final PlatformApiDataValidationException exception) {
        return exception.getErrors().stream().map(ApiParameterError::getUserMessageGlobalisationCode).toList();
    }
}

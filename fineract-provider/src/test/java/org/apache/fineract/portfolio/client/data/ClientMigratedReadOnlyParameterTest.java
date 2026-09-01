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
package org.apache.fineract.portfolio.client.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDate;
import java.util.HashMap;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.serialization.GoogleGsonSerializerHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClientMigratedReadOnlyParameterTest extends ClientApiCollectionConstants {

    private ClientDataValidator validator;

    @BeforeEach
    public void setUp() {
        this.validator = new ClientDataValidator(new FromJsonHelper(), null);

        final HashMap<BusinessDateType, LocalDate> businessDates = new HashMap<>();
        businessDates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 9, 1));
        businessDates.put(BusinessDateType.COB_DATE, LocalDate.of(2026, 8, 31));
        ThreadLocalContextUtil.setBusinessDates(businessDates);
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalContextUtil.clear();
    }

    @Test
    public void migratedIsAcceptedOnCreateSoTheMigrationTemplateCanSetIt() {
        final String json = "{\"officeId\":1,\"firstname\":\"Wanjiku\",\"lastname\":\"Kamau\",\"legalFormId\":1,\"migrated\":true}";

        final Throwable thrown = catchThrowable(() -> this.validator.validateForCreate(json));

        assertThat(thrown).isNotInstanceOf(UnsupportedParameterException.class);
    }

    @Test
    public void migratedIsRejectedOnUpdate() {
        final String json = "{\"firstname\":\"Wanjiku\",\"migrated\":true}";

        assertThatThrownBy(() -> this.validator.validateForUpdate(json))
                .isInstanceOfSatisfying(UnsupportedParameterException.class,
                        e -> assertThat(e.getUnsupportedParameters()).containsExactly(ClientApiConstants.migratedParamName));
    }

    @Test
    public void bulkImportPayloadCarriesMigratedOnlyWhenTheSheetSaysSo() {
        // The client bulk import Gson-serialises ClientData straight into the CREATE CLIENT command.
        assertThat(importPayload(Boolean.TRUE)).contains("\"migrated\":true");
        assertThat(importPayload(Boolean.FALSE)).contains("\"migrated\":false");
        // A blank cell must stay absent rather than serialise as null, so the row still validates.
        assertThat(importPayload(null)).doesNotContain(ClientApiConstants.migratedParamName);
    }

    @Test
    public void migratedIsSettableAtCreationButNeverOnUpdate() {
        assertThat(CLIENT_RESPONSE_DATA_PARAMETERS).contains(ClientApiConstants.migratedParamName);
        assertThat(CLIENT_CREATE_REQUEST_DATA_PARAMETERS).contains(ClientApiConstants.migratedParamName);
        assertThat(CLIENT_UPDATE_REQUEST_DATA_PARAMETERS).doesNotContain(ClientApiConstants.migratedParamName);
    }

    private static String importPayload(final Boolean migrated) {
        final ClientData imported = ClientData.importClientPersonInstance(1L, 1, "Wanjiku", "Kamau", null, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1), Boolean.TRUE, "KE-EXT-1", 120L, null, "254700000000", null, null, null, null, Boolean.FALSE, null,
                "en", "dd MMMM yyyy", null, migrated);

        return GoogleGsonSerializerHelper.createGsonBuilder().create().toJson(imported);
    }
}

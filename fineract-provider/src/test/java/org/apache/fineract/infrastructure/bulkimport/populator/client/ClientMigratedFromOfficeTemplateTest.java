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
package org.apache.fineract.infrastructure.bulkimport.populator.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.infrastructure.bulkimport.importhandler.ImportHandlerUtils;
import org.apache.fineract.infrastructure.bulkimport.populator.OfficeSheetPopulator;
import org.apache.fineract.infrastructure.bulkimport.populator.PersonnelSheetPopulator;
import org.apache.fineract.organisation.office.data.OfficeData;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.Test;

public class ClientMigratedFromOfficeTemplateTest {

    private static final OfficeData KENYA_CAPITAL = office(5L, "Kenya Capital");
    private static final OfficeData KENYA = office(3L, "Kenya");
    private static final OfficeData HEAD_OFFICE = office(1L, "Head Office");

    @Test
    public void personTemplateForOneOfficeOffersEveryOfficeAsTheMigratedFromOffice() {
        final List<OfficeData> downloadedFor = List.of(KENYA_CAPITAL);
        final Workbook workbook = new HSSFWorkbook();
        new ClientPersonWorkbookPopulator(new OfficeSheetPopulator(downloadedFor, List.of(HEAD_OFFICE, KENYA, KENYA_CAPITAL)),
                new PersonnelSheetPopulator(List.of(), downloadedFor), List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
                        .populate(workbook, "dd MMMM yyyy");

        assertMigratedFromOfficeOffersAllOffices(workbook);
    }

    @Test
    public void entityTemplateForOneOfficeOffersEveryOfficeAsTheMigratedFromOffice() {
        final List<OfficeData> downloadedFor = List.of(KENYA_CAPITAL);
        final Workbook workbook = new HSSFWorkbook();
        new ClientEntityWorkbookPopulator(new OfficeSheetPopulator(downloadedFor, List.of(HEAD_OFFICE, KENYA, KENYA_CAPITAL)),
                new PersonnelSheetPopulator(List.of(), downloadedFor), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of()).populate(workbook, "dd MMMM yyyy");

        assertMigratedFromOfficeOffersAllOffices(workbook);
    }

    @Test
    public void theClientOfficeDropdownStillOnlyOffersTheOfficeTheTemplateWasDownloadedFor() {
        final List<OfficeData> downloadedFor = List.of(KENYA_CAPITAL);
        final Workbook workbook = new HSSFWorkbook();
        new ClientPersonWorkbookPopulator(new OfficeSheetPopulator(downloadedFor, List.of(HEAD_OFFICE, KENYA, KENYA_CAPITAL)),
                new PersonnelSheetPopulator(List.of(), downloadedFor), List.of(), List.of(), List.of(), List.of(), List.of(), List.of())
                        .populate(workbook, "dd MMMM yyyy");

        assertThat(workbook.getName("Office").getRefersToFormula())
                .isEqualTo(TemplatePopulateImportConstants.OFFICE_SHEET_NAME + "!$B$2:$B$2");
    }

    private static void assertMigratedFromOfficeOffersAllOffices(final Workbook workbook) {
        assertThat(workbook.getName("MigratedFromOffice").getRefersToFormula())
                .isEqualTo(TemplatePopulateImportConstants.OFFICE_SHEET_NAME + "!$E$2:$E$4");

        final Sheet officeSheet = workbook.getSheet(TemplatePopulateImportConstants.OFFICE_SHEET_NAME);
        assertThat(ImportHandlerUtils.getIdByName(officeSheet, "Kenya")).isEqualTo(3L);
        assertThat(ImportHandlerUtils.getIdByName(officeSheet, "Head_Office")).isEqualTo(1L);
        assertThat(ImportHandlerUtils.getIdByName(officeSheet, "Kenya_Capital")).isEqualTo(5L);
    }

    private static OfficeData office(final Long id, final String name) {
        return new OfficeData(id, name, name, null, LocalDate.of(2020, 1, 1), ".", null, null, null);
    }
}

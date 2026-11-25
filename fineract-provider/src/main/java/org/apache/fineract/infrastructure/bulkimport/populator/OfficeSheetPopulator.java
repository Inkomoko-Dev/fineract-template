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
package org.apache.fineract.infrastructure.bulkimport.populator;

import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.organisation.office.data.OfficeData;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

public class OfficeSheetPopulator extends AbstractWorkbookPopulator {

    private List<OfficeData> offices;
    private List<OfficeData> migratedFromOffices;

    private static final int ID_COL = 0;
    private static final int OFFICE_NAME_COL = 1;
    private static final int MIGRATED_FROM_ID_COL = 3;
    private static final int MIGRATED_FROM_OFFICE_NAME_COL = 4;

    public OfficeSheetPopulator(final List<OfficeData> offices) {
        this.offices = offices;
    }

    public OfficeSheetPopulator(final List<OfficeData> offices, final List<OfficeData> migratedFromOffices) {
        this.offices = offices;
        this.migratedFromOffices = migratedFromOffices;
    }

    @Override
    public void populate(final Workbook workbook, String dateFormat) {
        int rowIndex = 1;
        Sheet officeSheet = workbook.createSheet(TemplatePopulateImportConstants.OFFICE_SHEET_NAME);
        setLayout(officeSheet);

        populateOffices(officeSheet, rowIndex);
        populateMigratedFromOffices(officeSheet);
        officeSheet.protectSheet("");
    }

    private void populateOffices(Sheet officeSheet, int rowIndex) {
        for (OfficeData office : offices) {
            Row row = officeSheet.createRow(rowIndex);
            writeLong(ID_COL, row, office.getId());
            writeString(OFFICE_NAME_COL, row, office.name().trim().replaceAll("[ )(/%-]", "_"));
            rowIndex++;
        }
    }

    private void populateMigratedFromOffices(Sheet officeSheet) {
        if (migratedFromOffices == null) {
            return;
        }
        Row rowHeader = officeSheet.getRow(TemplatePopulateImportConstants.ROWHEADER_INDEX);
        writeString(MIGRATED_FROM_ID_COL, rowHeader, "Migrated From ID");
        writeString(MIGRATED_FROM_OFFICE_NAME_COL, rowHeader, "Migrated From Office");
        officeSheet.setColumnWidth(MIGRATED_FROM_ID_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        officeSheet.setColumnWidth(MIGRATED_FROM_OFFICE_NAME_COL, TemplatePopulateImportConstants.MEDIUM_COL_SIZE);
        int rowIndex = 1;
        for (OfficeData office : migratedFromOffices) {
            Row row = officeSheet.getRow(rowIndex);
            if (row == null) {
                row = officeSheet.createRow(rowIndex);
            }
            writeLong(MIGRATED_FROM_ID_COL, row, office.getId());
            writeString(MIGRATED_FROM_OFFICE_NAME_COL, row, office.name().trim().replaceAll("[ )(/%-]", "_"));
            rowIndex++;
        }
    }

    public String getMigratedFromOfficeRangeFormula() {
        if (migratedFromOffices == null) {
            return TemplatePopulateImportConstants.OFFICE_SHEET_NAME + "!$B$2:$B$" + (offices.size() + 1);
        }
        return TemplatePopulateImportConstants.OFFICE_SHEET_NAME + "!$E$2:$E$" + (migratedFromOffices.size() + 1);
    }

    private void setLayout(Sheet worksheet) {
        worksheet.setColumnWidth(ID_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(OFFICE_NAME_COL, TemplatePopulateImportConstants.MEDIUM_COL_SIZE);
        Row rowHeader = worksheet.createRow(TemplatePopulateImportConstants.ROWHEADER_INDEX);
        rowHeader.setHeight(TemplatePopulateImportConstants.ROW_HEADER_HEIGHT);
        writeString(ID_COL, rowHeader, "ID");
        writeString(OFFICE_NAME_COL, rowHeader, "Name");
    }

    public List<OfficeData> getOffices() {
        return offices;
    }

    public List<String> getOfficeNames() {
        List<String> officeNames = new ArrayList<>();
        for (OfficeData office : offices) {
            officeNames.add(office.name());
        }
        return officeNames;
    }

}

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
package org.apache.fineract.infrastructure.bulkimport.populator.loanrepayment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.infrastructure.bulkimport.constants.LoanRepaymentConstants;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.infrastructure.bulkimport.populator.ExtrasSheetPopulator;
import org.apache.fineract.infrastructure.bulkimport.populator.OfficeSheetPopulator;
import org.apache.fineract.organisation.office.data.OfficeData;
import org.apache.fineract.portfolio.loanaccount.data.LoanRepaymentTemplateData;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.junit.jupiter.api.Test;

public class LoanRepaymentWorkbookPopulatorTest {

    @Test
    public void templateIncludesHierarchyOfficesAndMfiCodeLookup() {
        final List<OfficeData> offices = List.of(new OfficeData(1L, "Head Office", "Head Office", null, null, ".1.", null, null, null),
                new OfficeData(2L, "Child Office", "Child Office", null, null, ".1.2.", null, null, null));
        final LoanRepaymentTemplateData loan = new LoanRepaymentTemplateData("000001", "Active", 10L, "Amina", "EXT-10", "Working Capital",
                new BigDecimal("1000.00"), new BigDecimal("250.00"), LocalDate.of(2026, 1, 15), "Child Office", "MFI-001");
        final LoanRepaymentWorkbookPopulator populator = new LoanRepaymentWorkbookPopulator(List.of(loan), new OfficeSheetPopulator(offices),
                new ExtrasSheetPopulator(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList()));

        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            populator.populate(workbook, "dd MMMM yyyy");
            final Sheet sheet = workbook.getSheet(TemplatePopulateImportConstants.LOAN_REPAYMENT_SHEET_NAME);
            final Row header = sheet.getRow(0);
            assertEquals("MFI Code", header.getCell(LoanRepaymentConstants.MFI_CODE_COL).getStringCellValue());
            assertEquals("Loan Account No.*", header.getCell(LoanRepaymentConstants.LOAN_ACCOUNT_NO_COL).getStringCellValue());
            assertEquals("Lookup MFI Code", header.getCell(LoanRepaymentConstants.LOOKUP_MFI_CODE_COL).getStringCellValue());

            final Row loanRow = sheet.getRow(1);
            assertEquals("000001-Active", loanRow.getCell(LoanRepaymentConstants.LOOKUP_ACCOUNT_NO_COL).getStringCellValue());
            assertEquals("Child Office", loanRow.getCell(LoanRepaymentConstants.LOOKUP_OFFICE_NAME_COL).getStringCellValue());
            assertEquals("MFI-001", loanRow.getCell(LoanRepaymentConstants.LOOKUP_MFI_CODE_COL).getStringCellValue());

            final String officeFormula = loanRow.getCell(LoanRepaymentConstants.OFFICE_NAME_COL).getCellFormula();
            assertTrue(officeFormula.contains("VLOOKUP"));
            assertTrue(officeFormula.contains("$E2"));
            assertFalse(officeFormula.contains("Office!$B$2"));
            assertTrue(loanRow.getCell(LoanRepaymentConstants.MFI_CODE_COL).getCellFormula().contains("VLOOKUP"));

            final Sheet officeSheet = workbook.getSheet(TemplatePopulateImportConstants.OFFICE_SHEET_NAME);
            assertEquals("Head_Office", officeSheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals("Child_Office", officeSheet.getRow(2).getCell(1).getStringCellValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

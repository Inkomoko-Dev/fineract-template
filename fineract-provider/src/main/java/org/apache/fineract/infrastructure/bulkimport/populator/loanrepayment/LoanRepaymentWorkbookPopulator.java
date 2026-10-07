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

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import org.apache.fineract.infrastructure.bulkimport.constants.LoanRepaymentConstants;
import org.apache.fineract.infrastructure.bulkimport.constants.TemplatePopulateImportConstants;
import org.apache.fineract.infrastructure.bulkimport.populator.AbstractWorkbookPopulator;
import org.apache.fineract.infrastructure.bulkimport.populator.ExtrasSheetPopulator;
import org.apache.fineract.infrastructure.bulkimport.populator.OfficeSheetPopulator;
import org.apache.fineract.portfolio.loanaccount.data.LoanRepaymentTemplateData;
import org.apache.poi.hssf.usermodel.HSSFDataValidationHelper;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoanRepaymentWorkbookPopulator extends AbstractWorkbookPopulator {

    private static final Logger LOG = LoggerFactory.getLogger(LoanRepaymentWorkbookPopulator.class);
    private static final int INPUT_ROW_COUNT = 3000;

    private final OfficeSheetPopulator officeSheetPopulator;
    private final ExtrasSheetPopulator extrasSheetPopulator;
    private final List<LoanRepaymentTemplateData> allloans;

    public LoanRepaymentWorkbookPopulator(List<LoanRepaymentTemplateData> loans, OfficeSheetPopulator officeSheetPopulator,
            ExtrasSheetPopulator extrasSheetPopulator) {
        this.allloans = loans;
        this.officeSheetPopulator = officeSheetPopulator;
        this.extrasSheetPopulator = extrasSheetPopulator;
    }

    @Override
    public void populate(Workbook workbook, String dateFormat) {
        Sheet loanRepaymentSheet = workbook.createSheet(TemplatePopulateImportConstants.LOAN_REPAYMENT_SHEET_NAME);
        setLayout(loanRepaymentSheet);
        officeSheetPopulator.populate(workbook, dateFormat);
        extrasSheetPopulator.populate(workbook, dateFormat);
        populateLoansTable(loanRepaymentSheet, dateFormat);
        setRules(loanRepaymentSheet, dateFormat);
        setDefaults(loanRepaymentSheet, dateFormat);
    }

    private void setDefaults(Sheet worksheet, String dateFormat) {
        final String lookupRange = lookupRange();
        final Workbook workbook = worksheet.getWorkbook();
        final CellStyle dateCellStyle = workbook.createCellStyle();
        dateCellStyle.setDataFormat(workbook.createDataFormat().getFormat(dateFormat));
        for (int rowNo = 1; rowNo < INPUT_ROW_COUNT; rowNo++) {
            Row row = worksheet.getRow(rowNo);
            if (row == null) {
                row = worksheet.createRow(rowNo);
            }
            final int excelRow = rowNo + 1;
            writeFormula(LoanRepaymentConstants.OFFICE_NAME_COL, row, lookupFormula(excelRow, lookupRange,
                    LoanRepaymentConstants.LOOKUP_OFFICE_NAME_COL));
            writeFormula(LoanRepaymentConstants.CLIENT_NAME_COL, row,
                    lookupFormula(excelRow, lookupRange, LoanRepaymentConstants.LOOKUP_CLIENT_NAME_COL));
            writeFormula(LoanRepaymentConstants.CLIENT_EXTERNAL_ID, row,
                    lookupFormula(excelRow, lookupRange, LoanRepaymentConstants.LOOKUP_CLIENT_EXTERNAL_ID));
            writeFormula(LoanRepaymentConstants.MFI_CODE_COL, row,
                    lookupFormula(excelRow, lookupRange, LoanRepaymentConstants.LOOKUP_MFI_CODE_COL));
            writeFormula(LoanRepaymentConstants.PRODUCT_COL, row,
                    lookupFormula(excelRow, lookupRange, LoanRepaymentConstants.LOOKUP_PRODUCT_COL));
            writeFormula(LoanRepaymentConstants.PRINCIPAL_COL, row,
                    lookupFormula(excelRow, lookupRange, LoanRepaymentConstants.LOOKUP_PRINCIPAL_COL));
            writeFormula(LoanRepaymentConstants.TOTAL_OUTSTANDING_AMOUNT_COL, row,
                    lookupFormula(excelRow, lookupRange, LoanRepaymentConstants.LOOKUP_TOTAL_OUTSTANDING_AMOUNT_COL));
            writeFormula(LoanRepaymentConstants.LOAN_DISBURSEMENT_DATE_COL, row,
                    lookupFormula(excelRow, lookupRange, LoanRepaymentConstants.LOOKUP_LOAN_DISBURSEMENT_DATE_COL));
            row.getCell(LoanRepaymentConstants.LOAN_DISBURSEMENT_DATE_COL).setCellStyle(dateCellStyle);
        }
    }

    private String lookupFormula(final int excelRow, final String lookupRange, final int lookupColumn) {
        final String accountCell = "$" + columnName(LoanRepaymentConstants.LOAN_ACCOUNT_NO_COL) + excelRow;
        final int returnColumn = lookupColumn - LoanRepaymentConstants.LOOKUP_ACCOUNT_NO_COL + 1;
        return "IF(" + accountCell + "=\"\",\"\",IF(ISERROR(VLOOKUP(" + accountCell + "," + lookupRange + "," + returnColumn
                + ",FALSE)),\"\",VLOOKUP(" + accountCell + "," + lookupRange + "," + returnColumn + ",FALSE)))";
    }

    private String lookupRange() {
        final int endRow = Math.max(this.allloans.size() + 1, 2);
        return "$" + columnName(LoanRepaymentConstants.LOOKUP_ACCOUNT_NO_COL) + "$2:$"
                + columnName(LoanRepaymentConstants.LOOKUP_MFI_CODE_COL) + "$" + endRow;
    }

    private static String columnName(final int columnIndex) {
        return CellReference.convertNumToColString(columnIndex);
    }

    private void setRules(Sheet worksheet, String dateFormat) {
        CellRangeAddressList accountNumberRange = new CellRangeAddressList(1, SpreadsheetVersion.EXCEL97.getLastRowIndex(),
                LoanRepaymentConstants.LOAN_ACCOUNT_NO_COL, LoanRepaymentConstants.LOAN_ACCOUNT_NO_COL);
        CellRangeAddressList repaymentTypeRange = new CellRangeAddressList(1, SpreadsheetVersion.EXCEL97.getLastRowIndex(),
                LoanRepaymentConstants.REPAYMENT_TYPE_COL, LoanRepaymentConstants.REPAYMENT_TYPE_COL);
        CellRangeAddressList repaymentDateRange = new CellRangeAddressList(1, SpreadsheetVersion.EXCEL97.getLastRowIndex(),
                LoanRepaymentConstants.REPAID_ON_DATE_COL, LoanRepaymentConstants.REPAID_ON_DATE_COL);

        DataValidationHelper validationHelper = new HSSFDataValidationHelper((HSSFSheet) worksheet);

        setNames(worksheet);

        DataValidationConstraint accountNumberConstraint = validationHelper.createFormulaListConstraint("LoanAccounts");
        DataValidationConstraint paymentTypeConstraint = validationHelper.createFormulaListConstraint("PaymentTypes");
        DataValidationConstraint repaymentDateConstraint = validationHelper
                .createDateConstraint(DataValidationConstraint.OperatorType.BETWEEN, "01 January 1900", "=TODAY()", dateFormat);

        DataValidation accountNumberValidation = validationHelper.createValidation(accountNumberConstraint, accountNumberRange);
        DataValidation repaymentTypeValidation = validationHelper.createValidation(paymentTypeConstraint, repaymentTypeRange);
        DataValidation repaymentDateValidation = validationHelper.createValidation(repaymentDateConstraint, repaymentDateRange);

        worksheet.addValidationData(accountNumberValidation);
        worksheet.addValidationData(repaymentTypeValidation);
        worksheet.addValidationData(repaymentDateValidation);
    }

    private void setNames(Sheet worksheet) {
        Workbook loanRepaymentWorkbook = worksheet.getWorkbook();
        final int lookupEndRow = Math.max(allloans.size() + 1, 2);
        Name loanAccountGroup = loanRepaymentWorkbook.createName();
        loanAccountGroup.setNameName("LoanAccounts");
        loanAccountGroup.setRefersToFormula(TemplatePopulateImportConstants.LOAN_REPAYMENT_SHEET_NAME + "!$"
                + columnName(LoanRepaymentConstants.LOOKUP_ACCOUNT_NO_COL) + "$2:$"
                + columnName(LoanRepaymentConstants.LOOKUP_ACCOUNT_NO_COL) + "$" + lookupEndRow);

        LOG.info("All active loans: {}", allloans.size());

        Name paymentTypeGroup = loanRepaymentWorkbook.createName();
        paymentTypeGroup.setNameName("PaymentTypes");
        paymentTypeGroup.setRefersToFormula(TemplatePopulateImportConstants.EXTRAS_SHEET_NAME + "!$D$2:$D$"
                + Math.max(extrasSheetPopulator.getPaymentTypesSize() + 1, 2));
    }

    private void populateLoansTable(Sheet loanRepaymentSheet, String dateFormat) {
        int rowIndex = 1;
        Row row;
        Workbook workbook = loanRepaymentSheet.getWorkbook();
        CellStyle dateCellStyle = workbook.createCellStyle();
        short df = workbook.createDataFormat().getFormat(dateFormat);
        dateCellStyle.setDataFormat(df);
        DateTimeFormatter outputFormat = new DateTimeFormatterBuilder().appendPattern(dateFormat).toFormatter();
        for (LoanRepaymentTemplateData loan : allloans) {
            row = loanRepaymentSheet.getRow(rowIndex);
            if (row == null) {
                row = loanRepaymentSheet.createRow(rowIndex);
            }
            rowIndex++;
            writeString(LoanRepaymentConstants.LOOKUP_ACCOUNT_NO_COL, row, loan.getAccountNo() + "-" + loan.getStatusValue());
            if (loan.getClientName() != null && loan.getClientId() != null) {
                writeString(LoanRepaymentConstants.LOOKUP_CLIENT_NAME_COL, row, loan.getClientName() + "(" + loan.getClientId() + ")");
            }
            if (loan.getClientExternalId() != null) {
                writeString(LoanRepaymentConstants.LOOKUP_CLIENT_EXTERNAL_ID, row, loan.getClientExternalId());
            }
            if (loan.getProductName() != null) {
                writeString(LoanRepaymentConstants.LOOKUP_PRODUCT_COL, row, loan.getProductName());
            }
            if (loan.getPrincipal() != null) {
                writeDouble(LoanRepaymentConstants.LOOKUP_PRINCIPAL_COL, row, loan.getPrincipal().doubleValue());
            }
            if (loan.getTotalOutstanding() != null) {
                writeBigDecimal(LoanRepaymentConstants.LOOKUP_TOTAL_OUTSTANDING_AMOUNT_COL, row, loan.getTotalOutstanding());
            }
            if (loan.getDisbursementDate() != null) {
                writeDate(LoanRepaymentConstants.LOOKUP_LOAN_DISBURSEMENT_DATE_COL, row,
                        outputFormat.format(loan.getDisbursementDate()), dateCellStyle, dateFormat);
            }
            if (loan.getOfficeName() != null) {
                writeString(LoanRepaymentConstants.LOOKUP_OFFICE_NAME_COL, row, loan.getOfficeName().trim());
            }
            if (loan.getMfiCode() != null) {
                writeString(LoanRepaymentConstants.LOOKUP_MFI_CODE_COL, row, loan.getMfiCode());
            }
        }
    }

    private void setLayout(Sheet worksheet) {
        Row rowHeader = worksheet.createRow(TemplatePopulateImportConstants.ROWHEADER_INDEX);
        rowHeader.setHeight(TemplatePopulateImportConstants.ROW_HEADER_HEIGHT);
        worksheet.setColumnWidth(LoanRepaymentConstants.OFFICE_NAME_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.CLIENT_NAME_COL, TemplatePopulateImportConstants.MEDIUM_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.CLIENT_EXTERNAL_ID, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.MFI_CODE_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOAN_ACCOUNT_NO_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.PRODUCT_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.PRINCIPAL_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.TOTAL_OUTSTANDING_AMOUNT_COL, TemplatePopulateImportConstants.MEDIUM_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOAN_DISBURSEMENT_DATE_COL, TemplatePopulateImportConstants.MEDIUM_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.AMOUNT_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.REPAID_ON_DATE_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.REPAYMENT_TYPE_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.ACCOUNT_NO_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.CHECK_NO_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.RECEIPT_NO_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.ROUTING_CODE_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.BANK_NO_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);

        worksheet.setColumnWidth(LoanRepaymentConstants.LOOKUP_ACCOUNT_NO_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOOKUP_CLIENT_NAME_COL, TemplatePopulateImportConstants.MEDIUM_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOOKUP_CLIENT_EXTERNAL_ID, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOOKUP_PRODUCT_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOOKUP_PRINCIPAL_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOOKUP_TOTAL_OUTSTANDING_AMOUNT_COL,
                TemplatePopulateImportConstants.LARGE_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOOKUP_LOAN_DISBURSEMENT_DATE_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOOKUP_OFFICE_NAME_COL, TemplatePopulateImportConstants.MEDIUM_COL_SIZE);
        worksheet.setColumnWidth(LoanRepaymentConstants.LOOKUP_MFI_CODE_COL, TemplatePopulateImportConstants.SMALL_COL_SIZE);

        writeString(LoanRepaymentConstants.OFFICE_NAME_COL, rowHeader, "Office Name*");
        writeString(LoanRepaymentConstants.CLIENT_NAME_COL, rowHeader, "Client Name*");
        writeString(LoanRepaymentConstants.CLIENT_EXTERNAL_ID, rowHeader, "Client Ext.Id");
        writeString(LoanRepaymentConstants.MFI_CODE_COL, rowHeader, "MFI Code");
        writeString(LoanRepaymentConstants.LOAN_ACCOUNT_NO_COL, rowHeader, "Loan Account No.*");
        writeString(LoanRepaymentConstants.PRODUCT_COL, rowHeader, "Product Name");
        writeString(LoanRepaymentConstants.PRINCIPAL_COL, rowHeader, "Principal");
        writeString(LoanRepaymentConstants.TOTAL_OUTSTANDING_AMOUNT_COL, rowHeader, "Total Outstanding Amount");
        writeString(LoanRepaymentConstants.LOAN_DISBURSEMENT_DATE_COL, rowHeader, "Loan Disbursement Date");
        writeString(LoanRepaymentConstants.AMOUNT_COL, rowHeader, "Amount Repaid*");
        writeString(LoanRepaymentConstants.REPAID_ON_DATE_COL, rowHeader, "Date*");
        writeString(LoanRepaymentConstants.REPAYMENT_TYPE_COL, rowHeader, "Type*");
        writeString(LoanRepaymentConstants.ACCOUNT_NO_COL, rowHeader, "Account No");
        writeString(LoanRepaymentConstants.CHECK_NO_COL, rowHeader, "Check No");
        writeString(LoanRepaymentConstants.RECEIPT_NO_COL, rowHeader, "Receipt No");
        writeString(LoanRepaymentConstants.ROUTING_CODE_COL, rowHeader, "Routing Code");
        writeString(LoanRepaymentConstants.BANK_NO_COL, rowHeader, "Bank No");

        writeString(LoanRepaymentConstants.LOOKUP_ACCOUNT_NO_COL, rowHeader, "Lookup Account");
        writeString(LoanRepaymentConstants.LOOKUP_CLIENT_NAME_COL, rowHeader, "Lookup Client");
        writeString(LoanRepaymentConstants.LOOKUP_CLIENT_EXTERNAL_ID, rowHeader, "Lookup ClientExtId");
        writeString(LoanRepaymentConstants.LOOKUP_PRODUCT_COL, rowHeader, "Lookup Product");
        writeString(LoanRepaymentConstants.LOOKUP_PRINCIPAL_COL, rowHeader, "Lookup Principal");
        writeString(LoanRepaymentConstants.LOOKUP_TOTAL_OUTSTANDING_AMOUNT_COL, rowHeader, "Lookup Total Outstanding amount");
        writeString(LoanRepaymentConstants.LOOKUP_LOAN_DISBURSEMENT_DATE_COL, rowHeader, "Lookup Loan Disbursement Date");
        writeString(LoanRepaymentConstants.LOOKUP_OFFICE_NAME_COL, rowHeader, "Lookup Office");
        writeString(LoanRepaymentConstants.LOOKUP_MFI_CODE_COL, rowHeader, "Lookup MFI Code");
    }
}

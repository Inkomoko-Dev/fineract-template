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
package org.apache.fineract.accounting.provisioning.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournalLine;

@Getter
public class ProvisionBatchJournalLineData {

    private final Long id;
    private final Long glAccountId;
    private final String glAccountCode;
    private final String accountCode;
    private final String accountName;
    private final String entryType;
    private final BigDecimal debit;
    private final BigDecimal credit;
    private final LocalDate transactionDate;

    public ProvisionBatchJournalLineData(final Long id, final Long glAccountId, final String glAccountCode, final String accountCode,
            final String accountName, final String entryType, final BigDecimal debit, final BigDecimal credit,
            final LocalDate transactionDate) {
        this.id = id;
        this.glAccountId = glAccountId;
        this.glAccountCode = glAccountCode;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.entryType = entryType;
        this.debit = debit;
        this.credit = credit;
        this.transactionDate = transactionDate;
    }

    public static ProvisionBatchJournalLineData from(final ProvisionBatchJournalLine line) {
        final String glAccountCode = line.getGlAccountCode();
        return new ProvisionBatchJournalLineData(line.getId(), line.getGlAccountId(), glAccountCode, accountCode(glAccountCode),
                accountName(glAccountCode), line.getEntryType(), line.getDebitAmount(), line.getCreditAmount(),
                line.getTransactionDate());
    }

    public static String accountCode(final String glAccountCode) {
        if (glAccountCode == null || glAccountCode.isBlank()) {
            return "";
        }
        final int dash = glAccountCode.indexOf('-');
        return dash > 0 ? glAccountCode.substring(0, dash) : glAccountCode;
    }

    public static String accountName(final String glAccountCode) {
        if (glAccountCode == null || glAccountCode.isBlank()) {
            return "";
        }
        final int first = glAccountCode.indexOf('-');
        final int second = first >= 0 ? glAccountCode.indexOf('-', first + 1) : -1;
        if (first >= 0 && second > first && second + 1 < glAccountCode.length()) {
            return glAccountCode.substring(second + 1);
        }
        return glAccountCode;
    }
}

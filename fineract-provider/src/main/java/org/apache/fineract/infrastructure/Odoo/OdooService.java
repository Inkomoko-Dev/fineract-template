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
package org.apache.fineract.infrastructure.Odoo;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.provisioning.domain.ProvisionBatchJournal;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.portfolio.client.domain.Client;

public interface OdooService {

    Integer loginToOddo();

    public Integer createCustomerToOddo(Client client);

    public void postClientsToOddo() throws JobExecutionException;

    public Boolean updateCustomerToOddo(Client client);

    public void postCustomerUpdatedDetailsToOddo() throws JobExecutionException;

    JsonObject createJournalEntryToOddo(List<JournalEntry> list, Long loanTransactionId, Long transactionType, Boolean isReversed, String loanAccountNo, String location,Long fundSource)
            throws IOException, NoSuchAlgorithmException, KeyManagementException;

    String updateJournalEntryWithOdooStatus(String stringRequest);

    JsonObject postJournalEntryToOddo(LocalDate fromDate, LocalDate toDate, Long officeId, String currency);

    void postJournalEntryToOddo() throws JobExecutionException;

    void postClientToOdooOnCreateTask(Client client);

    void postClientToOdooOnUpdateTask(final Map<String, Object> changes, Client client);

    void postFailedClientsOnMigration(Client client, String errorMsg, String jsonObject);

    void postFailedLoansOnMigration(BigDecimal amount, Long clientID, String odooLoanNumber, String odooLoanId, String errorMsg,
            String jsonObject);

    void postFailedLoanRepaymentOnMigration(BigDecimal transactionAmount, Long loanId, String transactionDate, String note,
            String paymentType, String errorMsg, String jsonObject);

    /**
     * Builds the same JournalData/JournalItemData/JournalEntryToOdooData payload
     * shape used for loan transactions, but for an aggregated provisioning
     * journal - and only builds/returns the JSON, it does not call Odoo.
     *
     * @param journal
     *            the aggregated provisioning journal (one office + currency,
     *            with one line per GL account touched).
     * @param isReversed
     *            whether this journal is itself a reversal of a prior period's
     *            journal.
     */
    String buildProvisioningJournalEntryPayload(ProvisionBatchJournal journal, boolean isReversed);

}

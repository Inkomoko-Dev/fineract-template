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
package org.apache.fineract.accounting.journalentry.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;
import org.apache.fineract.accounting.journalentry.data.JournalEntryAssociationParametersData;
import org.junit.jupiter.api.Test;

/**
 * CGLT-779: GET /journalentries?transactionId=...&amp;transactionDetails=true must join payment details and notes with
 * single-equality conditions so the database can use an index instead of scanning m_payment_detail and m_note.
 */
public class JournalEntryTransactionDetailsSqlTest {

    private static String schema(final boolean transactionDetails) throws Exception {
        final Class<?> mapperClass = Class
                .forName(JournalEntryReadPlatformServiceImpl.class.getName() + "$GLJournalEntryMapper");
        final Constructor<?> constructor = mapperClass.getDeclaredConstructor(JournalEntryAssociationParametersData.class);
        constructor.setAccessible(true);
        final Object mapper = constructor.newInstance(new JournalEntryAssociationParametersData(transactionDetails, false));
        final Method schema = mapperClass.getDeclaredMethod("schema");
        schema.setAccessible(true);
        return ((String) schema.invoke(mapper)).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String joinClause(final String sql, final String joinStart) {
        final int start = sql.indexOf(joinStart);
        assertTrue(start >= 0, "missing join: " + joinStart);
        final int next = sql.indexOf(" left join ", start + joinStart.length());
        return next < 0 ? sql.substring(start) : sql.substring(start, next);
    }

    @Test
    public void paymentDetailIsJoinedOnItsPrimaryKeyWithoutOr() throws Exception {
        final String join = joinClause(schema(true), "left join m_payment_detail as pd");
        assertFalse(join.contains(" or "), join);
        assertTrue(join.contains("pd.id = coalesce(lt.payment_detail_id, st.payment_detail_id, journalentry.payment_details_id)"), join);
    }

    @Test
    public void notesAreJoinedSeparatelyForLoanAndSavingsTransactions() throws Exception {
        final String sql = schema(true);
        assertFalse(sql.contains("m_note as note on"), sql);
        final String loanNote = joinClause(sql, "left join m_note as loannote");
        final String savingsNote = joinClause(sql, "left join m_note as savingsnote");
        assertFalse(loanNote.contains(" or "), loanNote);
        assertFalse(savingsNote.contains(" or "), savingsNote);
        assertTrue(loanNote.contains("loannote.loan_transaction_id = lt.id"), loanNote);
        assertTrue(savingsNote.contains("savingsnote.savings_account_transaction_id = st.id"), savingsNote);
        assertTrue(sql.contains("coalesce(loannote.id, savingsnote.id) as noteid"), sql);
        assertTrue(sql.contains("coalesce(loannote.note, savingsnote.note) as transactionnote"), sql);
    }

    @Test
    public void listWithoutTransactionDetailsSkipsTheDetailJoins() throws Exception {
        final String sql = schema(false);
        assertFalse(sql.contains("m_payment_detail"), sql);
        assertFalse(sql.contains("m_note"), sql);
    }
}

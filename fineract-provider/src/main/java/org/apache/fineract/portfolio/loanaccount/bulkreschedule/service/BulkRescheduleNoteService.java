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
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for adding notes to loans during bulk reschedule operations.
 * Creates audit trail entries linking reschedule operations to loans.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRescheduleNoteService {

    private final NoteRepository noteRepository;

    /**
     * Adds a reschedule note to a loan for audit and reference purposes.
     * 
     * @param loan the loan to add note to
     * @param execution the workflow metadata recorded in the note
     */
    @Transactional
    public void addRescheduleNoteToLoan(final Loan loan, final BulkRescheduleExecution execution) {
        final String marker = "[Bulk reschedule execution #" + execution.getId() + "]";
        if (noteRepository.existsLoanNoteContaining(loan.getId(), "%" + marker + "%")) {
            return;
        }
        final String initiator = execution.getUser() == null ? "unknown" : execution.getUser().getUsername();
        final String approver = execution.getApprover() == null ? "unknown" : execution.getApprover().getUsername();
        final String noteText = marker + " Initiated by: " + initiator + "; approved by: " + approver
                + "; reschedule reason: " + valueOrDash(execution.getSubmissionNote()) + "; approval reason: "
                + valueOrDash(execution.getApprovalNote()) + "; approved at: " + valueOrDash(execution.getApprovedAt()) + ".";
        noteRepository.save(Note.loanNote(loan, noteText));
        log.info("Added bulk reschedule note to loan {} for execution {}", loan.getId(), execution.getId());
    }

    private String valueOrDash(final Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}

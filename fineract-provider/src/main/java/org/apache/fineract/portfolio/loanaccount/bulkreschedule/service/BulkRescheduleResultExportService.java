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

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.ws.rs.core.StreamingOutput;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.data.BulkRescheduleLoanPreviewDto;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleExecution;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Streams the same result shape used by preview as a spreadsheet-safe CSV file. */
@Service
@RequiredArgsConstructor
public class BulkRescheduleResultExportService {

    private static final List<String> HEADERS = List.of("Loan ID", "Loan Account", "Client", "Office", "Product", "Loan Officer",
            "Loan Status", "Result", "Current Interest Rate", "New Interest Rate", "Current Outstanding", "New Outstanding",
            "Current Term", "New Term", "Next Installment", "Reschedule Reason", "Result Reason");

    private final BulkRescheduleResultRepository resultRepository;

    public StreamingOutput csv(final BulkRescheduleExecution execution) {
        return output -> {
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write('\ufeff');
            writeRow(writer, HEADERS);
            int pageNumber = 0;
            boolean hasMore;
            do {
                final var page = resultRepository.findPageByExecutionId(execution.getId(),
                        PageRequest.of(pageNumber, 500, Sort.by("id").ascending()));
                for (final var result : page.getContent()) {
                    final BulkRescheduleLoanPreviewDto row = BulkRescheduleLoanPreviewDto.fromResult(result);
                    writeRow(writer, List.of(value(row.getLoanId()), value(row.getLoanAccountNumber()), value(row.getClientName()),
                            value(row.getOfficeName()), value(row.getLoanProductName()), value(row.getLoanOfficerName()),
                            value(row.getLoanStatus()), value(row.getStatus()), value(row.getCurrentInterestRate()),
                            value(row.getNewInterestRate()), value(row.getTotalOutstanding()), value(row.getNewTotalOutstanding()),
                            value(row.getCurrentTerm()), value(row.getNewTerm()), value(row.getNextScheduledInstallment()),
                            value(row.getRescheduleReason()), value(row.getResultReason())));
                }
                hasMore = page.hasNext();
                pageNumber++;
            } while (hasMore);
            writer.flush();
        };
    }

    private void writeRow(final BufferedWriter writer, final List<String> values) throws java.io.IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(csvValue(values.get(index)));
        }
        writer.newLine();
    }

    private String value(final Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String csvValue(final String rawValue) {
        String value = rawValue == null ? "" : rawValue;
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}

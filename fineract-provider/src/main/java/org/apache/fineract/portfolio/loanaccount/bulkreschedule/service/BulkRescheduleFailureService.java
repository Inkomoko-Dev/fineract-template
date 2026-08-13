
package org.apache.fineract.portfolio.loanaccount.bulkreschedule.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.domain.BulkRescheduleResult.BulkRescheduleResultStatus;
import org.apache.fineract.portfolio.loanaccount.bulkreschedule.repository.BulkRescheduleResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists a failure after the failed loan transaction has ended. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkRescheduleFailureService {

    private final BulkRescheduleResultRepository resultRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(final Long resultId, final Long executionId, final Long loanId, final Exception failure) {
        final BulkRescheduleResult result = resultRepository.findById(resultId).orElse(null);
        if (result == null || !executionId.equals(result.getExecution().getId()) || !loanId.equals(result.getLoanId())) {
            log.error("Could not persist failure for execution {} loan {}", executionId, loanId, failure);
            return;
        }
        result.setStatus(BulkRescheduleResultStatus.FAILED);
        result.setErrorMessage(normalize(failure));
        resultRepository.save(result);
    }

    private String normalize(final Exception failure) {
        final String message = failure.getMessage();
        return message == null || message.trim().isEmpty() ? failure.getClass().getSimpleName() : message;
    }
}
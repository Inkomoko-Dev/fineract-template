package org.apache.fineract.portfolio.loanaccount.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrancheReleaseNoticeRepository extends JpaRepository<TrancheReleaseNotice, Long> {

    boolean existsByLoanIdAndDisbursementDetailId(Long loanId, Long disbursementDetailId);

    void deleteByLoanIdAndDisbursementDetailId(Long loanId, Long disbursementDetailId);
}

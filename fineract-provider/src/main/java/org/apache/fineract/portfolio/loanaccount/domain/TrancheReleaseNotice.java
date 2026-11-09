package org.apache.fineract.portfolio.loanaccount.domain;

import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_loan_tranche_release_notice", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "loan_id", "disbursement_detail_id" }) })
public class TrancheReleaseNotice extends AbstractPersistableCustom {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "disbursement_detail_id", nullable = false)
    private Long disbursementDetailId;

    @Column(name = "notified_on", nullable = false)
    private OffsetDateTime notifiedOn;

    protected TrancheReleaseNotice() {}

    public TrancheReleaseNotice(final Long loanId, final Long disbursementDetailId, final OffsetDateTime notifiedOn) {
        this.loanId = loanId;
        this.disbursementDetailId = disbursementDetailId;
        this.notifiedOn = notifiedOn;
    }

    public Long getLoanId() {
        return loanId;
    }

    public Long getDisbursementDetailId() {
        return disbursementDetailId;
    }
}

package com.srm.invoice.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.OrgUnit;
import com.srm.master.domain.Supplier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@Entity
@Table(name = "reconciliation")
public class Reconciliation extends BaseEntity {

    @Column(name = "recon_no", nullable = false, length = 64, unique = true)
    private String reconNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_org_id", nullable = false)
    private OrgUnit procurementOrg;

    @Column(name = "period_from")
    private LocalDate periodFrom;

    @Column(name = "period_to")
    private LocalDate periodTo;

    @Column(name = "po_amount", precision = 19, scale = 4)
    private BigDecimal poAmount = BigDecimal.ZERO;

    @Column(name = "gr_amount", precision = 19, scale = 4)
    private BigDecimal grAmount = BigDecimal.ZERO;

    @Column(name = "invoice_amount", precision = 19, scale = 4)
    private BigDecimal invoiceAmount = BigDecimal.ZERO;

    @Column(name = "diff_amount", precision = 19, scale = 4)
    private BigDecimal diffAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReconStatus status = ReconStatus.DRAFT;

    @Column(length = 1000)
    private String remark;
}

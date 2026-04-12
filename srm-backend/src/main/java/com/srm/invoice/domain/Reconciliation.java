package com.srm.invoice.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.OrgUnit;
import com.srm.master.domain.Supplier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
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

    /**
     * 对账按收货月时，与 {@link #grAmount} 同为期间内按收货行×订单行单价汇总（入库执行额）。
     */
    @Column(name = "po_amount", precision = 19, scale = 4)
    private BigDecimal poAmount = BigDecimal.ZERO;

    /** 对账期间内（收货单 {@code receipt_date}）的收货计价金额 */
    @Column(name = "gr_amount", precision = 19, scale = 4)
    private BigDecimal grAmount = BigDecimal.ZERO;

    /**
     * 已确认发票中、发票行关联之收货单的收货日期落在对账期间内的行金额合计。
     */
    @Column(name = "invoice_amount", precision = 19, scale = 4)
    private BigDecimal invoiceAmount = BigDecimal.ZERO;

    @Column(name = "diff_amount", precision = 19, scale = 4)
    private BigDecimal diffAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReconStatus status = ReconStatus.PENDING_SUPPLIER;

    @Column(length = 1000)
    private String remark;

    @Column(name = "supplier_confirmed_at")
    private Instant supplierConfirmedAt;

    @Column(name = "procurement_confirmed_at")
    private Instant procurementConfirmedAt;

    @Column(name = "dispute_reason", length = 1000)
    private String disputeReason;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    /** SUPPLIER | PROCUREMENT */
    @Column(name = "disputed_by", length = 32)
    private String disputedBy;

    @Column(name = "procurement_reject_reason", length = 1000)
    private String procurementRejectReason;
}

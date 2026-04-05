package com.srm.execution.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.OrgUnit;
import com.srm.master.domain.Supplier;
import com.srm.po.domain.PurchaseOrder;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "asn_notice")
public class AsnNotice extends BaseEntity {

    @Column(name = "asn_no", nullable = false, length = 64, unique = true)
    private String asnNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_org_id", nullable = false)
    private OrgUnit procurementOrg;

    @Column(name = "ship_date", nullable = false)
    private LocalDate shipDate;

    @Column(name = "eta_date")
    private LocalDate etaDate;

    @Column(length = 255)
    private String carrier;

    @Column(name = "tracking_no", length = 128)
    private String trackingNo;

    @Column(length = 1000)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AsnStatus status = AsnStatus.SUBMITTED;

    @OneToMany(mappedBy = "asnNotice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<AsnLine> lines = new ArrayList<>();
}

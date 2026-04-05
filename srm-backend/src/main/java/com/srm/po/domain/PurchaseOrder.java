package com.srm.po.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import com.srm.master.domain.Supplier;
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

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "purchase_order")
public class PurchaseOrder extends BaseEntity {

    @Column(name = "po_no", nullable = false, length = 64, unique = true)
    private String poNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_org_id", nullable = false)
    private OrgUnit procurementOrg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private Ledger ledger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(nullable = false, length = 8)
    private String currency = "CNY";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PoStatus status = PoStatus.DRAFT;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo = 1;

    @Column(length = 2000)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_status", nullable = false, length = 32)
    private ExportStatus exportStatus = ExportStatus.NOT_EXPORTED;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<PurchaseOrderLine> lines = new ArrayList<>();
}

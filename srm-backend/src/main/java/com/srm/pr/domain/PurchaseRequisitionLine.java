package com.srm.pr.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.Warehouse;
import com.srm.master.domain.MaterialItem;
import com.srm.master.domain.Supplier;
import com.srm.po.domain.PurchaseOrder;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
@Entity
@Table(name = "purchase_requisition_line")
public class PurchaseRequisitionLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pr_id", nullable = false)
    private PurchaseRequisition purchaseRequisition;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private MaterialItem material;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal qty;

    @Column(nullable = false, length = 32)
    private String uom;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(length = 500)
    private String remark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_po_id")
    private PurchaseOrder convertedPo;
}

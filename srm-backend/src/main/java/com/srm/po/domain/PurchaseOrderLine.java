package com.srm.po.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.Warehouse;
import com.srm.master.domain.MaterialItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "purchase_order_line")
public class PurchaseOrderLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private MaterialItem material;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal qty;

    @Column(nullable = false, length = 32)
    private String uom;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "requested_date")
    private LocalDate requestedDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "confirmed_qty", precision = 19, scale = 4)
    private BigDecimal confirmedQty;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Column(name = "supplier_remark", length = 500)
    private String supplierRemark;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** 累计实收数量 */
    @Column(name = "received_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedQty = BigDecimal.ZERO;
}

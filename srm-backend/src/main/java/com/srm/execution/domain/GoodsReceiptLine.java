package com.srm.execution.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.po.domain.PurchaseOrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "goods_receipt_line")
public class GoodsReceiptLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_line_id", nullable = false)
    private PurchaseOrderLine purchaseOrderLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asn_line_id")
    private AsnLine asnLine;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "received_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedQty;
}

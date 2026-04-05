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
@Table(name = "asn_line")
public class AsnLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asn_notice_id", nullable = false)
    private AsnNotice asnNotice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_line_id", nullable = false)
    private PurchaseOrderLine purchaseOrderLine;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "ship_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal shipQty;
}

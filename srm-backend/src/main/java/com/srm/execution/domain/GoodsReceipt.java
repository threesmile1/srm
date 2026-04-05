package com.srm.execution.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.Ledger;
import com.srm.foundation.domain.OrgUnit;
import com.srm.foundation.domain.Warehouse;
import com.srm.master.domain.Supplier;
import com.srm.po.domain.ExportStatus;
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
@Table(name = "goods_receipt")
public class GoodsReceipt extends BaseEntity {

    @Column(name = "gr_no", nullable = false, length = 64, unique = true)
    private String grNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_org_id", nullable = false)
    private OrgUnit procurementOrg;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private Ledger ledger;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "receipt_date", nullable = false)
    private LocalDate receiptDate;

    @Column(length = 1000)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_status", nullable = false, length = 32)
    private ExportStatus exportStatus = ExportStatus.NOT_EXPORTED;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<GoodsReceiptLine> lines = new ArrayList<>();
}

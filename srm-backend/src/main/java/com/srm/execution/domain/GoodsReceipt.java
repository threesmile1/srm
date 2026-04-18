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

    /**
     * 收货单来源系统：SRM / U9（用于宁波「U9 状态门禁」等逻辑）。
     * 为空时按 SRM 处理（兼容历史数据）。
     */
    @Column(name = "source_system", length = 32)
    private String sourceSystem;

    /** U9 收货单号/单据编号（幂等键）；仅 U9 同步写入。 */
    @Column(name = "u9_doc_no", length = 64)
    private String u9DocNo;

    /** U9 业务状态（原样保存），如：审核中/业务关闭。仅 U9 同步写入。 */
    @Column(name = "u9_status", length = 64)
    private String u9Status;

    /**
     * 兼容字段：历史/展示用；收货单不再走客服审批。新建收货单恒为 {@link GrStatus#APPROVED}。
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GrStatus status = GrStatus.APPROVED;

    @OneToMany(mappedBy = "goodsReceipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<GoodsReceiptLine> lines = new ArrayList<>();
}

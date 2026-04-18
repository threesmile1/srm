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
import java.time.LocalDate;
import java.time.Instant;

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

    /** U9 采购订单号（PM_PurchaseOrder.DocNo），与 procurement_org_id 组合唯一，用于帆软 caigou 同步幂等 */
    @Column(name = "u9_doc_no", length = 128)
    private String u9DocNo;

    /** U9 业务日期（用于采购执行报表展示） */
    @Column(name = "u9_business_date")
    private LocalDate u9BusinessDate;

    /** 正式订单号（DescFlexField_PrivateDescSeg5） */
    @Column(name = "u9_official_order_no", length = 128)
    private String u9OfficialOrderNo;

    /** 二级门店（DescFlexField_PrivateDescSeg8） */
    @Column(name = "u9_store2", length = 128)
    private String u9Store2;

    /** 收货人名称（DescFlexField_PrivateDescSeg3） */
    @Column(name = "u9_receiver_name", length = 128)
    private String u9ReceiverName;

    /** 终端电话（DescFlexField_PrivateDescSeg11） */
    @Column(name = "u9_terminal_phone", length = 64)
    private String u9TerminalPhone;

    /** 安装地址（DescFlexField_PrivateDescSeg10） */
    @Column(name = "u9_install_address", length = 512)
    private String u9InstallAddress;

    /**
     * U9 业务是否关闭：1=业务关闭。
     * 由宁波 U9 采购订单接口同步写入，用于后续业务门禁/展示。
     */
    @Column(name = "u9_business_closed")
    private Boolean u9BusinessClosed;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_status", nullable = false, length = 32)
    private ExportStatus exportStatus = ExportStatus.NOT_EXPORTED;

    @Column(name = "released_at")
    private Instant releasedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<PurchaseOrderLine> lines = new ArrayList<>();
}

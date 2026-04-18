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

import java.time.Instant;
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

    /** 物流单/快递单上的收货信息（可由附件识别回填） */
    @Column(name = "receiver_name", length = 255)
    private String receiverName;

    @Column(name = "receiver_phone", length = 128)
    private String receiverPhone;

    @Column(name = "receiver_address", length = 1000)
    private String receiverAddress;

    /** 物流单附件（单文件） */
    @Column(name = "logistics_attachment_original_name", length = 500)
    private String logisticsAttachmentOriginalName;

    @Column(name = "logistics_attachment_content_type", length = 200)
    private String logisticsAttachmentContentType;

    @Column(name = "logistics_attachment_file_size")
    private Long logisticsAttachmentFileSize;

    /** 相对 {@code srm.asn-upload-dir} 的路径，如 {asnId}/{uuid}.jpg */
    @Column(name = "logistics_attachment_stored_path", length = 1000)
    private String logisticsAttachmentStoredPath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AsnStatus status = AsnStatus.SUBMITTED;

    /** 宁波：客服确认记录（不阻断收货），由审批中心处理后写回。 */
    @Column(name = "cs_confirm_status", length = 32)
    private String csConfirmStatus;

    @Column(name = "cs_confirmer_id")
    private Long csConfirmerId;

    @Column(name = "cs_confirmer_name", length = 128)
    private String csConfirmerName;

    @Column(name = "cs_confirmed_at")
    private Instant csConfirmedAt;

    @Column(name = "cs_confirm_comment", length = 1000)
    private String csConfirmComment;

    @OneToMany(mappedBy = "asnNotice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<AsnLine> lines = new ArrayList<>();
}

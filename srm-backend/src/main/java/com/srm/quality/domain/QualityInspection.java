package com.srm.quality.domain;

import com.srm.execution.domain.GoodsReceipt;
import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.OrgUnit;
import com.srm.master.domain.Supplier;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "quality_inspection")
public class QualityInspection extends BaseEntity {

    @Column(name = "inspection_no", nullable = false, unique = true, length = 64)
    private String inspectionNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt goodsReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_org_id", nullable = false)
    private OrgUnit procurementOrg;

    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "inspector_name", length = 64)
    private String inspectorName;

    @Column(nullable = false, length = 32)
    private String result; // PASS, FAIL, CONDITIONAL

    @Column(name = "total_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalQty;

    @Column(name = "qualified_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal qualifiedQty = BigDecimal.ZERO;

    @Column(name = "defect_qty", nullable = false, precision = 19, scale = 4)
    private BigDecimal defectQty = BigDecimal.ZERO;

    @Column(name = "defect_type", length = 128)
    private String defectType;

    @Column(length = 1000)
    private String remark;
}

package com.srm.contract.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.master.domain.MaterialItem;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "contract_line")
public class ContractLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private MaterialItem material;

    @Column(name = "material_desc")
    private String materialDesc;

    @Column(precision = 19, scale = 4)
    private BigDecimal qty;

    @Column(length = 16)
    private String uom;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 500)
    private String remark;
}

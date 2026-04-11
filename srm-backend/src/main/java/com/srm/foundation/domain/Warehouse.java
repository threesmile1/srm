package com.srm.foundation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "warehouse", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wh_org_code", columnNames = {"procurement_org_id", "code"})
})
public class Warehouse extends BaseEntity {

    /** 隶属采购组织（工厂） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "procurement_org_id", nullable = false)
    private OrgUnit procurementOrg;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    /** U9 cangku.cpt：工厂/品类 */
    @Column(name = "u9_gongchang", length = 255)
    private String u9Gongchang;

    @Column(name = "u9_wh_code", length = 64)
    private String u9WhCode;
}

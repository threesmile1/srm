package com.srm.foundation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "org_unit", uniqueConstraints = {
        @UniqueConstraint(name = "uk_org_unit_ledger_code", columnNames = {"ledger_id", "code"})
})
public class OrgUnit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private Ledger ledger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrgUnitType orgType;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    /** 与 U9 组织编码对齐 */
    @Column(name = "u9_org_code", length = 64)
    private String u9OrgCode;
}

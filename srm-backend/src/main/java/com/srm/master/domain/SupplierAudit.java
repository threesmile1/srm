package com.srm.master.domain;

import com.srm.foundation.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "supplier_audit")
public class SupplierAudit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "audit_type", nullable = false, length = 64)
    private String auditType;

    @Column(name = "audit_date", nullable = false)
    private LocalDate auditDate;

    @Column(nullable = false, length = 32)
    private String result;

    @Column
    private Integer score;

    @Column(name = "auditor_name", length = 128)
    private String auditorName;

    @Column(length = 1000)
    private String remark;
}

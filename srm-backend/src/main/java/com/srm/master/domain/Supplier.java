package com.srm.master.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.OrgUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "supplier")
public class Supplier extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "u9_vendor_code", length = 64)
    private String u9VendorCode;

    @Column(name = "tax_id", length = 64)
    private String taxId;

    /** 可协作的采购组织（门户授权范围） */
    @ManyToMany
    @JoinTable(
            name = "supplier_org_scope",
            joinColumns = @JoinColumn(name = "supplier_id"),
            inverseJoinColumns = @JoinColumn(name = "procurement_org_id")
    )
    private Set<OrgUnit> authorizedProcurementOrgs = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private SupplierLifecycleStatus lifecycleStatus = SupplierLifecycleStatus.QUALIFIED;

    @Column(name = "contact_name", length = 128)
    private String contactName;

    @Column(name = "contact_phone", length = 64)
    private String contactPhone;

    @Column(name = "contact_email", length = 128)
    private String contactEmail;

    @Column(length = 500)
    private String address;

    @Column(name = "bank_name", length = 128)
    private String bankName;

    @Column(name = "bank_account", length = 64)
    private String bankAccount;

    @Column(name = "business_scope", length = 1000)
    private String businessScope;

    @Column(name = "registration_remark", length = 1000)
    private String registrationRemark;
}

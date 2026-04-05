package com.srm.master.domain;

import com.srm.foundation.domain.BaseEntity;
import com.srm.foundation.domain.OrgUnit;
import jakarta.persistence.Entity;
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

    @jakarta.persistence.Column(nullable = false, length = 64, unique = true)
    private String code;

    @jakarta.persistence.Column(nullable = false, length = 255)
    private String name;

    @jakarta.persistence.Column(name = "u9_vendor_code", length = 64)
    private String u9VendorCode;

    @jakarta.persistence.Column(name = "tax_id", length = 64)
    private String taxId;

    /** 可协作的采购组织（门户授权范围） */
    @ManyToMany
    @JoinTable(
            name = "supplier_org_scope",
            joinColumns = @JoinColumn(name = "supplier_id"),
            inverseJoinColumns = @JoinColumn(name = "procurement_org_id")
    )
    private Set<OrgUnit> authorizedProcurementOrgs = new HashSet<>();
}

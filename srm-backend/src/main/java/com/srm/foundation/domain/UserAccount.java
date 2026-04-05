package com.srm.foundation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.srm.master.domain.Supplier;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "sys_user")
public class UserAccount extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String username;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 128)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 默认采购组织（数据权限主入口，后续可扩展多组织） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_procurement_org_id")
    private OrgUnit defaultProcurementOrg;

    /** 门户用户关联供应商（内部用户为空） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}

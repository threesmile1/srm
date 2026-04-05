package com.srm.foundation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sys_role")
public class Role extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;
}

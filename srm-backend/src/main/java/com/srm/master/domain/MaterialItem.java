package com.srm.master.domain;

import com.srm.foundation.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "material_item")
public class MaterialItem extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 32)
    private String uom;

    @Column(name = "u9_item_code", length = 64)
    private String u9ItemCode;
}

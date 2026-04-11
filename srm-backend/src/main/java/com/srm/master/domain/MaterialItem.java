package com.srm.master.domain;

import com.srm.foundation.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

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

    /** 规格型号（U9 同步） */
    @Column(length = 512)
    private String specification;

    /** 参考采购单价（U9 同步） */
    @Column(name = "purchase_unit_price", precision = 19, scale = 4)
    private BigDecimal purchaseUnitPrice;

    /** 苏州工厂存储仓库（cangku_yigui.cpt：cangku_suzhou） */
    @Column(name = "u9_warehouse_suzhou", length = 255)
    private String u9WarehouseSuzhou;

    /** 成都工厂存储仓库（cangku_yigui.cpt：cangku_chengdu） */
    @Column(name = "u9_warehouse_chengdu", length = 255)
    private String u9WarehouseChengdu;

    /** 华南工厂存储仓库（cangku_yigui.cpt：cangku_huanan） */
    @Column(name = "u9_warehouse_huanan", length = 255)
    private String u9WarehouseHuanan;

    /** 水漆工厂存储仓库（cangku_shuiqi.cpt） */
    @Column(name = "u9_warehouse_shuiqi", length = 255)
    private String u9WarehouseShuiqi;

    @Column(name = "u9_supplier_code", length = 64)
    private String u9SupplierCode;

    @Column(name = "u9_supplier_name", length = 255)
    private String u9SupplierName;
}

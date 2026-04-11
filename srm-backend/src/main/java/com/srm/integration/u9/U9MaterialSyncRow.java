package com.srm.integration.u9;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.math.BigDecimal;

/**
 * U9 物料同步行（wuliao.cpt 等接口返回 JSON 映射；支持中英文字段别名）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class U9MaterialSyncRow {

    /** 帆软 wuliao 等：liaohao=料号/编码 */
    @JsonAlias({"code", "liaohao", "物料编码", "itemCode", "ItemCode", "item_code"})
    private String code;

    /** 帆软：pinming=品名/物料名称 */
    @JsonAlias({"name", "pinming", "物料名称", "Name", "item_name", "品名"})
    private String name;

    /** 帆软：guige=规格 */
    @JsonAlias({"specification", "guige", "规格", "规格型号", "spec", "Spec"})
    private String specification;

    /** 帆软：price=采购单价（空串需宽松反序列化，否则会整批失败） */
    @JsonDeserialize(using = LenientBigDecimalDeserializer.class)
    @JsonAlias({"purchaseUnitPrice", "price", "采购单价", "unitPrice", "参考价"})
    private BigDecimal purchaseUnitPrice;

    @JsonAlias({"warehouseName", "仓库名称", "仓库", "warehouse", "WhName"})
    private String warehouseName;

    @JsonAlias({"supplierCode", "供应商编码", "vendorCode", "lpgys"})
    private String supplierCode;

    @JsonAlias({"supplierName", "供应商名称", "供应商", "vendorName"})
    private String supplierName;

    /** 帆软：jijiadanwei=计价单位 */
    @JsonAlias({"uom", "jijiadanwei", "单位", "UOM", "main_uom"})
    private String uom;

    @JsonAlias({"u9ItemCode", "U9料号", "u9_item_code", "ItemMaster"})
    private String u9ItemCode;
}

package com.srm.integration.u9;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 帆软 lpgys.cpt：按料号查询供应商，常见字段 code=供应商编码、gongyingshang=供应商名称；第三列可为料号。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class U9LpgysSupplierRow {

    @JsonProperty("code")
    @JsonAlias({"gysbm", "vendor_code", "supplier_code", "供应商编码"})
    private String supplierCode;

    @JsonProperty("gongyingshang")
    @JsonAlias({"supplierName", "supplier", "供应商", "供应商名称", "name"})
    private String supplierName;

    @JsonAlias({"liaohao", "物料编码", "wlh", "wuliaobianma", "pinming", "item_code"})
    private String materialCode;
}

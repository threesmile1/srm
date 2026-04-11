package com.srm.integration.u9;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 帆软 lpgys：入参为料品编码；返回常见字段
 * {@code lp_code}/{@code lp_name} 料品，{@code gongyingshang_code}/{@code gongyingshang_name} 供应商（兼容旧模板列名）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class U9LpgysSupplierRow {

    @JsonProperty("gongyingshang_code")
    @JsonAlias({"code", "gysbm", "vendor_code", "supplier_code", "供应商编码", "supplierCode", "lpgys",
            "VENDOR_CODE", "首供编码", "主供编码", "默认供应商编码", "供应商", "gongyingshangbianma"})
    private String supplierCode;

    @JsonProperty("gongyingshang_name")
    @JsonAlias({"gongyignshang_name", "gongyingshang", "supplierName", "supplier", "供应商名称", "供应商简称",
            "name", "gysmc", "gongyingshangmingcheng"})
    private String supplierName;

    @JsonProperty("lp_code")
    @JsonAlias({"liaohao", "物料编码", "wlh", "wuliaobianma", "pinming", "item_code"})
    private String materialCode;

    /** 料品名称（报表展示用，落库可不填） */
    @JsonProperty("lp_name")
    private String materialName;
}

package com.srm.integration.u9;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 帆软 cangku.cpt：gongchang 工厂/品类、code 仓库编码、name 仓库名称。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class U9CangkuRow {

    @JsonProperty("gongchang")
    @JsonAlias({"工厂", "品类", "factory", "org", "采购组织"})
    private String gongchang;

    @JsonProperty("code")
    @JsonAlias({"仓库编码", "wh_code", "cangku"})
    private String code;

    @JsonProperty("name")
    @JsonAlias({"仓库名称", "cangkumingcheng", "warehouseName"})
    private String name;
}

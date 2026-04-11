package com.srm.integration.u9;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 帆软 cangku_shuiqi.cpt：料号与水漆厂默认仓库。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class U9MaterialShuiqiRow {

    @JsonProperty("liaohao")
    @JsonAlias({"料号", "LIAOHAO", "item_code", "code", "物料编码", "ItemCode", "itemCode", "ITEM_CODE",
            "material_code", "MaterialCode"})
    private String liaohao;

    @JsonProperty("cangku_shuiqi")
    @JsonAlias({"水漆仓库", "水漆仓", "仓库水漆", "shuiqi", "warehouse_shuiqi", "cangkuShuiqi"})
    private String cangkuShuiqi;
}

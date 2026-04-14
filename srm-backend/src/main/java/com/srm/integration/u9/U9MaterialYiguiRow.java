package com.srm.integration.u9;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * 帆软 cangku_yigui.cpt：料号与各厂默认仓库。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class U9MaterialYiguiRow {

    @JsonProperty("liaohao")
    @JsonAlias({"料号", "LIAOHAO", "item_code", "code", "物料编码", "ItemCode", "itemCode", "ITEM_CODE",
            "material_code", "MaterialCode"})
    private String liaohao;

    @JsonProperty("cangku_suzhou")
    @JsonAlias({"苏州仓库", "苏州仓", "仓库苏州", "仓库_苏州", "suzhou", "cangkuSuzhou"})
    private String cangkuSuzhou;

    @JsonProperty("cangku_chengdu")
    @JsonAlias({"成都仓库", "成都仓", "仓库成都", "仓库_成都", "chengdu", "cangkuChengdu"})
    private String cangkuChengdu;

    @JsonProperty("cangku_huanan")
    @JsonAlias({"华南仓库", "华南仓", "仓库华南", "仓库_华南", "huanan", "cangkuHuanan"})
    private String cangkuHuanan;

    @JsonProperty("cangku_ningbo")
    @JsonAlias({"宁波仓库", "宁波仓", "仓库宁波", "仓库_宁波", "ningbo", "cangkuNingbo"})
    private String cangkuNingbo;
}

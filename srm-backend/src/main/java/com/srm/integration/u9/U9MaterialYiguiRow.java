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
    @JsonAlias({"料号", "LIAOHAO", "item_code", "code"})
    private String liaohao;

    @JsonProperty("cangku_suzhou")
    @JsonAlias({"苏州仓库", "suzhou", "cangkuSuzhou"})
    private String cangkuSuzhou;

    @JsonProperty("cangku_chengdu")
    @JsonAlias({"成都仓库", "chengdu", "cangkuChengdu"})
    private String cangkuChengdu;

    @JsonProperty("cangku_huanan")
    @JsonAlias({"华南仓库", "huanan", "cangkuHuanan"})
    private String cangkuHuanan;
}

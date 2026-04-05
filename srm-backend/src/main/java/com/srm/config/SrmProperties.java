package com.srm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Getter
@Setter
@ConfigurationProperties(prefix = "srm")
public class SrmProperties {

    /** 允许超收比例，例如 0.05 = 5% */
    private BigDecimal overReceiveRatio = new BigDecimal("0.05");

    private String exportPoTypeCode = "PO01";

    private String exportGrTypeCode = "GR01";
}

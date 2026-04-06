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

    /**
     * 业务时区（JVM 默认时区、合同到期 cron、建议与 Jackson 一致）。生产固定为 Asia/Shanghai。
     */
    private String businessTimezone = "Asia/Shanghai";
}

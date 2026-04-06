package com.srm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 在 Spring 环境就绪后、容器刷新前设置 JVM 默认时区，使 {@link java.time.LocalDate#now()}、
 * JPA {@code LocalDate} 等与业务日历一致。生产建议 {@code srm.business-timezone: Asia/Shanghai}。
 */
@Slf4j
public class BusinessTimezoneApplicationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    public static final String DEFAULT_ZONE_ID = "Asia/Shanghai";

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        String id = env.getProperty("srm.business-timezone", DEFAULT_ZONE_ID);
        try {
            ZoneId z = ZoneId.of(id.trim());
            TimeZone.setDefault(TimeZone.getTimeZone(z));
        } catch (DateTimeException ex) {
            log.warn("无效 srm.business-timezone={}，回退为 {}: {}", id, DEFAULT_ZONE_ID, ex.getMessage());
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(DEFAULT_ZONE_ID)));
        }
    }
}

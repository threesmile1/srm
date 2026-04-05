package com.srm.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 开发环境：在 migrate 之前先 repair，把 flyway_schema_history 的 checksum 与当前脚本对齐。
 * <p>
 * 仅 {@code dev} profile 生效；Spring 默认会在 validate 阶段因 checksum 不一致而失败，
 * 仅靠 {@code spring.flyway.repair-on-migrate} 在部分版本/顺序下不足以先 repair，故用显式策略。
 */
@Configuration
@Profile("dev")
public class DevFlywayRepairConfig {

    @Bean
    public FlywayMigrationStrategy devFlywayMigrationStrategy() {
        return (Flyway flyway) -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}

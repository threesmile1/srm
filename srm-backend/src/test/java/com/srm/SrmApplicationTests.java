package com.srm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SrmApplicationTests {

    @Test
    void contextLoads() {
        // 冒烟：验证主配置、JPA、Security 等可装配（不连 MySQL、不跑 Flyway）
    }
}

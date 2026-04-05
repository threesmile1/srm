package com.srm;

import org.springframework.boot.SpringApplication;
import com.srm.config.SrmProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties(SrmProperties.class)
public class SrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(SrmApplication.class, args);
    }
}

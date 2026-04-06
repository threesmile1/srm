package com.srm;

import com.srm.config.BusinessTimezoneApplicationListener;
import com.srm.config.SrmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(SrmProperties.class)
public class SrmApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SrmApplication.class);
        app.addListeners(new BusinessTimezoneApplicationListener());
        app.run(args);
    }
}

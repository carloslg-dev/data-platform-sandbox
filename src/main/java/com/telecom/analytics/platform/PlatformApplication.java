package com.telecom.analytics.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.telecom.analytics.platform.infrastructure.adapter.outbound.persistence.postgres")
@EnableMongoRepositories(basePackages = "com.telecom.analytics.platform.infrastructure.adapter.outbound.persistence.mongodb")
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}

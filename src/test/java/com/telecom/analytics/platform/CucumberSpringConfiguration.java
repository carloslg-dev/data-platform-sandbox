package com.telecom.analytics.platform;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;

@CucumberContextConfiguration
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CucumberSpringConfiguration {

    // Register Dynamic Properties for Testcontainers with local fallback
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (StepDefinitions.useTestcontainers) {
            registry.add("spring.datasource.url", StepDefinitions.postgres::getJdbcUrl);
            registry.add("spring.datasource.username", StepDefinitions.postgres::getUsername);
            registry.add("spring.datasource.password", StepDefinitions.postgres::getPassword);
            registry.add("spring.data.mongodb.uri", StepDefinitions.mongo::getReplicaSetUrl);
            registry.add("spring.cloud.aws.sqs.endpoint", () -> StepDefinitions.localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
            registry.add("spring.cloud.aws.region.static", StepDefinitions.localstack::getRegion);
            registry.add("spring.cloud.aws.credentials.access-key", StepDefinitions.localstack::getAccessKey);
            registry.add("spring.cloud.aws.credentials.secret-key", StepDefinitions.localstack::getSecretKey);
        } else {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/telecom_db");
            registry.add("spring.datasource.username", () -> "postgres");
            registry.add("spring.datasource.password", () -> "postgres");
            registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/telemetry");
            registry.add("spring.cloud.aws.sqs.endpoint", () -> "http://localhost:4566");
            registry.add("spring.cloud.aws.region.static", () -> "us-east-1");
            registry.add("spring.cloud.aws.credentials.access-key", () -> "noop");
            registry.add("spring.cloud.aws.credentials.secret-key", () -> "noop");
        }
        registry.add("telemetry.queue-name", () -> "telemetry-events.fifo");
    }
}

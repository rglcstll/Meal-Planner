package com.example.demo.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionProfileConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(TestConfig.class)
        .withPropertyValues(
            "spring.profiles.active=prod",
            "DB_URL=jdbc:mysql://example:3306/appdev",
            "DB_USERNAME=user",
            "DB_PASSWORD=password",
            "MAIL_USERNAME=test@example.com",
            "MAIL_PASSWORD=test-password",
            "APP_BASE_URL=http://localhost:8080/mealplanner",
            "CORS_ALLOWED_ORIGINS=http://localhost:3000"
        );

    @Test
    void productionProfileUsesValidateAndFlywayBaseline() {
        contextRunner.run(context -> {
            var env = context.getEnvironment();
            assertEquals("validate", env.getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("true", env.getProperty("spring.flyway.enabled"));
            assertEquals("true", env.getProperty("spring.flyway.baseline-on-migrate"));
            assertEquals("1", env.getProperty("spring.flyway.baseline-version"));
            assertFalse(Boolean.parseBoolean(env.getProperty("spring.jpa.show-sql", "false")));
        });
    }

    @Test
    void riskyLazyLoadNoTransPropertyIsNotConfigured() {
        contextRunner.run(context -> {
            var env = context.getEnvironment();
            assertNull(env.getProperty("spring.jpa.properties.hibernate.enable_lazy_load_no_trans"));
        });
    }

    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
    })
    @EnableConfigurationProperties
    static class TestConfig {
    }
}

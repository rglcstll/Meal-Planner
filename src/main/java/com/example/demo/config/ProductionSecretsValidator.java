package com.example.demo.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductionSecretsValidator implements ApplicationRunner {

    private final Environment environment;

    @Value("${app.security.require-secrets:true}")
    private boolean requireSecrets;

    public ProductionSecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!requireSecrets || !isProductionProfileActive()) {
            return;
        }

        List<String> missing = new ArrayList<>();
        requireText("spring.datasource.url", missing);
        requireText("spring.datasource.username", missing);
        requireText("spring.datasource.password", missing);
        requireText("spring.mail.username", missing);
        requireText("spring.mail.password", missing);
        requireText("app.base-url", missing);
        requireText("cors.allowed-origins", missing);

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                "Missing required production configuration values: " + String.join(", ", missing)
            );
        }
    }

    private void requireText(String key, List<String> missing) {
        if (!StringUtils.hasText(environment.getProperty(key))) {
            missing.add(key);
        }
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }
}

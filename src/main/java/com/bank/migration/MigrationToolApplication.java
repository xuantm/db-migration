package com.bank.migration;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.orchestrator.MigrationOrchestrator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(MigrationProperties.class)
public class MigrationToolApplication {
    public static void main(String[] args) {
        SpringApplication.run(MigrationToolApplication.class, args);
    }

    @Bean
    CommandLineRunner runMigration(MigrationProperties properties, MigrationOrchestrator orchestrator) {
        return args -> orchestrator.run(properties);
    }
}

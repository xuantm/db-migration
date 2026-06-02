package com.bank.migration;

import com.bank.migration.config.MigrationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MigrationProperties.class)
public class MigrationToolApplication {
    public static void main(String[] args) {
        SpringApplication.run(MigrationToolApplication.class, args);
    }
}

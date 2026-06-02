package com.bank.migration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "migration")
public record MigrationProperties(
    @Valid @NotNull Database source,
    @Valid @NotNull Database target,
    boolean cleanLoad,
    @Valid @NotNull Batch batch,
    @Valid @NotNull Reports reports
) {
    public record Database(
        @NotBlank String jdbcUrl,
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String driverClassName,
        @NotBlank String schema
    ) {}

    public record Batch(
        @Min(1) int chunkSize,
        @Min(1) int fetchSize,
        @Min(1) int maxParallelTables
    ) {}

    public record Reports(@NotBlank String outputDir) {}
}

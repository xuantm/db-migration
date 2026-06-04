package com.bank.migration.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.manifest.ManifestCacheProperties;
import com.bank.migration.types.UnsupportedTypePolicy;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "migration")
public record MigrationProperties(
    @Valid @NotNull Database source,
    @Valid @NotNull Database target,
    boolean cleanLoad,
    @Valid @NotNull Batch batch,
    @Valid @NotNull Reports reports,
    IdentifierMappingPolicy identifierPolicy,
    @Valid Excluded excluded,
    @Valid ManifestCacheProperties manifestCache,
    UnsupportedTypePolicy unsupportedTypePolicy
) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public MigrationProperties {
        if (excluded == null) {
            excluded = new Excluded(List.of(), List.of(), List.of());
        }
        if (manifestCache == null) {
            manifestCache = new ManifestCacheProperties(false, false, false, "DEFAULT_KEY", false);
        }
        if (unsupportedTypePolicy == null) {
            unsupportedTypePolicy = UnsupportedTypePolicy.FAIL;
        }
    }

    public MigrationProperties(
        Database source,
        Database target,
        boolean cleanLoad,
        Batch batch,
        Reports reports,
        IdentifierMappingPolicy identifierPolicy,
        Excluded excluded,
        ManifestCacheProperties manifestCache
    ) {
        this(source, target, cleanLoad, batch, reports, identifierPolicy, excluded, manifestCache, UnsupportedTypePolicy.FAIL);
    }

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

    public record Excluded(
        List<String> tables,
        List<String> views,
        List<String> sequences
    ) {
        public Excluded {
            if (tables == null) tables = List.of();
            if (views == null) views = List.of();
            if (sequences == null) sequences = List.of();
        }
    }
}

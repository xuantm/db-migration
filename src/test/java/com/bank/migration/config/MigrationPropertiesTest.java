package com.bank.migration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class MigrationPropertiesTest {
    @Test
    void bindsMigrationProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
            "migration.source.jdbc-url", "jdbc:oracle:thin:@//oracle-host:1521/BANK",
            "migration.source.username", "bank_src",
            "migration.source.password", "secret",
            "migration.source.driver-class-name", "oracle.jdbc.OracleDriver",
            "migration.source.schema", "BANK_CORE",
            "migration.target.jdbc-url", "jdbc:postgresql://gauss-host:5432/bank",
            "migration.target.username", "bank_dst",
            "migration.target.password", "secret",
            "migration.target.driver-class-name", "org.postgresql.Driver",
            "migration.target.schema", "BANK_CORE",
            "migration.clean-load", "true",
            "migration.batch.chunk-size", "5000",
            "migration.batch.fetch-size", "5000",
            "migration.batch.max-parallel-tables", "2",
            "migration.reports.output-dir", "build/migration-reports"
        ));

        MigrationProperties props = new Binder(source)
            .bind("migration", Bindable.of(MigrationProperties.class))
            .orElseThrow();

        assertThat(props.cleanLoad()).isTrue();
        assertThat(props.source().schema()).isEqualTo("BANK_CORE");
        assertThat(props.target().schema()).isEqualTo("BANK_CORE");
        assertThat(props.batch().chunkSize()).isEqualTo(5000);
        assertThat(props.reports().outputDir()).isEqualTo("build/migration-reports");
    }
}

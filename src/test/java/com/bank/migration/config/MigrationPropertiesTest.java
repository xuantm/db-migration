package com.bank.migration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

class MigrationPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            ConfigurationPropertiesAutoConfiguration.class,
            ValidationAutoConfiguration.class
        ))
        .withUserConfiguration(TestConfig.class);

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

    @Test
    void bindsMigrationPropertiesInSpringContext() {
        contextRunner
            .withPropertyValues(
                "migration.source.jdbc-url=jdbc:oracle:thin:@//oracle-host:1521/BANK",
                "migration.source.username=bank_src",
                "migration.source.password=secret",
                "migration.source.driver-class-name=oracle.jdbc.OracleDriver",
                "migration.source.schema=BANK_CORE",
                "migration.target.jdbc-url=jdbc:postgresql://gauss-host:5432/bank",
                "migration.target.username=bank_dst",
                "migration.target.password=secret",
                "migration.target.driver-class-name=org.postgresql.Driver",
                "migration.target.schema=BANK_CORE",
                "migration.clean-load=true",
                "migration.batch.chunk-size=5000",
                "migration.batch.fetch-size=5000",
                "migration.batch.max-parallel-tables=2",
                "migration.reports.output-dir=build/migration-reports"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();

                MigrationProperties props = context.getBean(MigrationProperties.class);

                assertThat(props.cleanLoad()).isTrue();
                assertThat(props.source().schema()).isEqualTo("BANK_CORE");
                assertThat(props.target().schema()).isEqualTo("BANK_CORE");
                assertThat(props.batch().chunkSize()).isEqualTo(5000);
                assertThat(props.reports().outputDir()).isEqualTo("build/migration-reports");
            });
    }

    @Test
    void rejectsInvalidBatchChunkSize() {
        contextRunner
            .withPropertyValues(
                "migration.source.jdbc-url=jdbc:oracle:thin:@//oracle-host:1521/BANK",
                "migration.source.username=bank_src",
                "migration.source.password=secret",
                "migration.source.driver-class-name=oracle.jdbc.OracleDriver",
                "migration.source.schema=BANK_CORE",
                "migration.target.jdbc-url=jdbc:postgresql://gauss-host:5432/bank",
                "migration.target.username=bank_dst",
                "migration.target.password=secret",
                "migration.target.driver-class-name=org.postgresql.Driver",
                "migration.target.schema=BANK_CORE",
                "migration.clean-load=true",
                "migration.batch.chunk-size=0",
                "migration.batch.fetch-size=5000",
                "migration.batch.max-parallel-tables=2",
                "migration.reports.output-dir=build/migration-reports"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasMessageContaining("chunkSize")
                    .hasMessageContaining("must be greater than or equal to 1");
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MigrationProperties.class)
    static class TestConfig {
    }
}

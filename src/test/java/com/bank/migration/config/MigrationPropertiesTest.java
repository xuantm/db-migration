package com.bank.migration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.types.OracleToGaussTypeMapper;
import com.bank.migration.types.TypeMappingResult;
import com.bank.migration.types.UnsupportedTypePolicy;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
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
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.ofEntries(
            Map.entry("migration.source.jdbc-url", "jdbc:oracle:thin:@//oracle-host:1521/BANK"),
            Map.entry("migration.source.username", "bank_src"),
            Map.entry("migration.source.password", "secret"),
            Map.entry("migration.source.driver-class-name", "oracle.jdbc.OracleDriver"),
            Map.entry("migration.source.schema", "BANK_CORE"),
            Map.entry("migration.target.jdbc-url", "jdbc:postgresql://gauss-host:5432/bank"),
            Map.entry("migration.target.username", "bank_dst"),
            Map.entry("migration.target.password", "secret"),
            Map.entry("migration.target.driver-class-name", "org.postgresql.Driver"),
            Map.entry("migration.target.schema", "BANK_CORE"),
            Map.entry("migration.clean-load", "true"),
            Map.entry("migration.batch.chunk-size", "5000"),
            Map.entry("migration.batch.fetch-size", "5000"),
            Map.entry("migration.batch.max-parallel-tables", "2"),
            Map.entry("migration.reports.output-dir", "build/migration-reports")
        ));

        MigrationProperties props = new Binder(source)
            .bind("migration", Bindable.of(MigrationProperties.class))
            .orElseThrow(() -> new IllegalStateException("Migration properties did not bind"));

        assertThat(props.cleanLoad()).isTrue();
        assertThat(props.source().schema()).isEqualTo("BANK_CORE");
        assertThat(props.target().schema()).isEqualTo("BANK_CORE");
        assertThat(props.batch().chunkSize()).isEqualTo(5000);
        assertThat(props.reports().outputDir()).isEqualTo("build/migration-reports");
        assertThat(props.unsupportedTypePolicy()).isEqualTo(UnsupportedTypePolicy.FAIL);
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
                assertThat(props.unsupportedTypePolicy()).isEqualTo(UnsupportedTypePolicy.FAIL);
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
                    .hasRootCauseInstanceOf(BindValidationException.class)
                    .rootCause()
                    .hasMessageContaining("migration.batch")
                    .hasMessageContaining("chunkSize")
                    .hasMessageContaining("must be greater than or equal to 1");
            });
    }

    @Test
    void bindsExcludedProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.ofEntries(
            Map.entry("migration.source.jdbc-url", "jdbc:oracle:thin:@//oracle-host:1521/BANK"),
            Map.entry("migration.source.username", "bank_src"),
            Map.entry("migration.source.password", "secret"),
            Map.entry("migration.source.driver-class-name", "oracle.jdbc.OracleDriver"),
            Map.entry("migration.source.schema", "BANK_CORE"),
            Map.entry("migration.target.jdbc-url", "jdbc:postgresql://gauss-host:5432/bank"),
            Map.entry("migration.target.username", "bank_dst"),
            Map.entry("migration.target.password", "secret"),
            Map.entry("migration.target.driver-class-name", "org.postgresql.Driver"),
            Map.entry("migration.target.schema", "BANK_CORE"),
            Map.entry("migration.clean-load", "true"),
            Map.entry("migration.batch.chunk-size", "5000"),
            Map.entry("migration.batch.fetch-size", "5000"),
            Map.entry("migration.batch.max-parallel-tables", "2"),
            Map.entry("migration.reports.output-dir", "build/migration-reports"),
            Map.entry("migration.excluded.tables[0]", "EBA_ATM"),
            Map.entry("migration.excluded.views[0]", "V_AUDIT"),
            Map.entry("migration.excluded.sequences[0]", "AUDIT_SEQ")
        ));

        MigrationProperties props = new Binder(source)
            .bind("migration", Bindable.of(MigrationProperties.class))
            .orElseThrow(() -> new IllegalStateException("Migration properties did not bind"));

        assertThat(props.excluded()).isNotNull();
        assertThat(props.excluded().tables()).containsExactly("EBA_ATM");
        assertThat(props.excluded().views()).containsExactly("V_AUDIT");
        assertThat(props.excluded().sequences()).containsExactly("AUDIT_SEQ");
    }

    @Test
    void defaultsExcludedPropertiesToEmptyLists() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.ofEntries(
            Map.entry("migration.source.jdbc-url", "jdbc:oracle:thin:@//oracle-host:1521/BANK"),
            Map.entry("migration.source.username", "bank_src"),
            Map.entry("migration.source.password", "secret"),
            Map.entry("migration.source.driver-class-name", "oracle.jdbc.OracleDriver"),
            Map.entry("migration.source.schema", "BANK_CORE"),
            Map.entry("migration.target.jdbc-url", "jdbc:postgresql://gauss-host:5432/bank"),
            Map.entry("migration.target.username", "bank_dst"),
            Map.entry("migration.target.password", "secret"),
            Map.entry("migration.target.driver-class-name", "org.postgresql.Driver"),
            Map.entry("migration.target.schema", "BANK_CORE"),
            Map.entry("migration.clean-load", "true"),
            Map.entry("migration.batch.chunk-size", "5000"),
            Map.entry("migration.batch.fetch-size", "5000"),
            Map.entry("migration.batch.max-parallel-tables", "2"),
            Map.entry("migration.reports.output-dir", "build/migration-reports")
        ));

        MigrationProperties props = new Binder(source)
            .bind("migration", Bindable.of(MigrationProperties.class))
            .orElseThrow(() -> new IllegalStateException("Migration properties did not bind"));

        assertThat(props.excluded()).isNotNull();
        assertThat(props.excluded().tables()).isEmpty();
        assertThat(props.excluded().views()).isEmpty();
        assertThat(props.excluded().sequences()).isEmpty();
    }

    @Test
    void bindsUnsupportedTypePolicy() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.ofEntries(
            Map.entry("migration.source.jdbc-url", "jdbc:oracle:thin:@//oracle-host:1521/BANK"),
            Map.entry("migration.source.username", "bank_src"),
            Map.entry("migration.source.password", "secret"),
            Map.entry("migration.source.driver-class-name", "oracle.jdbc.OracleDriver"),
            Map.entry("migration.source.schema", "BANK_CORE"),
            Map.entry("migration.target.jdbc-url", "jdbc:postgresql://gauss-host:5432/bank"),
            Map.entry("migration.target.username", "bank_dst"),
            Map.entry("migration.target.password", "secret"),
            Map.entry("migration.target.driver-class-name", "org.postgresql.Driver"),
            Map.entry("migration.target.schema", "BANK_CORE"),
            Map.entry("migration.clean-load", "true"),
            Map.entry("migration.batch.chunk-size", "5000"),
            Map.entry("migration.batch.fetch-size", "5000"),
            Map.entry("migration.batch.max-parallel-tables", "2"),
            Map.entry("migration.reports.output-dir", "build/migration-reports"),
            Map.entry("migration.unsupported-type-policy", "FALLBACK_TO_TEXT")
        ));

        MigrationProperties props = new Binder(source)
            .bind("migration", Bindable.of(MigrationProperties.class))
            .orElseThrow(() -> new IllegalStateException("Migration properties did not bind"));

        assertThat(props.unsupportedTypePolicy()).isEqualTo(UnsupportedTypePolicy.FALLBACK_TO_TEXT);
    }

    @Test
    void springManagedTypeMapperUsesFallbackPolicy() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                ConfigurationPropertiesAutoConfiguration.class,
                ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class)
            .withBean(OracleToGaussTypeMapper.class)
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
                "migration.reports.output-dir=build/migration-reports",
                "migration.unsupported-type-policy=FALLBACK_TO_TEXT"
            )
            .run(context -> {
                assertThat(context).hasNotFailed();

                OracleToGaussTypeMapper mapper = context.getBean(OracleToGaussTypeMapper.class);
                ColumnMetadata col = new ColumnMetadata("COL", "MY_CUSTOM_TYPE", null, null, true, null);
                TypeMappingResult result = mapper.map(col);

                assertThat(result.targetSqlType()).isEqualTo("text");
                assertThat(result.canGenerateDdl()).isTrue();
                assertThat(result.notes()).containsExactly("Unsupported Oracle type MY_CUSTOM_TYPE fell back to text");
            });
    }

    @Test
    void bindsManifestCacheProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.ofEntries(
            Map.entry("migration.source.jdbc-url", "jdbc:oracle:thin:@//oracle-host:1521/BANK"),
            Map.entry("migration.source.username", "bank_src"),
            Map.entry("migration.source.password", "secret"),
            Map.entry("migration.source.driver-class-name", "oracle.jdbc.OracleDriver"),
            Map.entry("migration.source.schema", "BANK_CORE"),
            Map.entry("migration.target.jdbc-url", "jdbc:postgresql://gauss-host:5432/bank"),
            Map.entry("migration.target.username", "bank_dst"),
            Map.entry("migration.target.password", "secret"),
            Map.entry("migration.target.driver-class-name", "org.postgresql.Driver"),
            Map.entry("migration.target.schema", "BANK_CORE"),
            Map.entry("migration.clean-load", "true"),
            Map.entry("migration.batch.chunk-size", "5000"),
            Map.entry("migration.batch.fetch-size", "5000"),
            Map.entry("migration.batch.max-parallel-tables", "2"),
            Map.entry("migration.reports.output-dir", "build/migration-reports"),
            Map.entry("migration.manifest-cache.enabled", "true"),
            Map.entry("migration.manifest-cache.load-from-cache", "true"),
            Map.entry("migration.manifest-cache.save-after-scan", "true"),
            Map.entry("migration.manifest-cache.cache-key", "my-custom-cache-key"),
            Map.entry("migration.manifest-cache.fail-if-cache-missing", "true")
        ));

        MigrationProperties props = new Binder(source)
            .bind("migration", Bindable.of(MigrationProperties.class))
            .orElseThrow(() -> new IllegalStateException("Migration properties did not bind"));

        assertThat(props.manifestCache()).isNotNull();
        assertThat(props.manifestCache().enabled()).isTrue();
        assertThat(props.manifestCache().loadFromCache()).isTrue();
        assertThat(props.manifestCache().saveAfterScan()).isTrue();
        assertThat(props.manifestCache().cacheKey()).isEqualTo("my-custom-cache-key");
        assertThat(props.manifestCache().failIfCacheMissing()).isTrue();
    }

    private MapConfigurationPropertySource basePropertySource() {
        return new MapConfigurationPropertySource(Map.ofEntries(
            Map.entry("migration.source.jdbc-url", "jdbc:oracle:thin:@//oracle-host:1521/BANK"),
            Map.entry("migration.source.username", "bank_src"),
            Map.entry("migration.source.password", "secret"),
            Map.entry("migration.source.driver-class-name", "oracle.jdbc.OracleDriver"),
            Map.entry("migration.source.schema", "BANK_CORE"),
            Map.entry("migration.target.jdbc-url", "jdbc:postgresql://gauss-host:5432/bank"),
            Map.entry("migration.target.username", "bank_dst"),
            Map.entry("migration.target.password", "secret"),
            Map.entry("migration.target.driver-class-name", "org.postgresql.Driver"),
            Map.entry("migration.target.schema", "BANK_CORE"),
            Map.entry("migration.clean-load", "true"),
            Map.entry("migration.batch.chunk-size", "5000"),
            Map.entry("migration.batch.fetch-size", "5000"),
            Map.entry("migration.batch.max-parallel-tables", "2"),
            Map.entry("migration.reports.output-dir", "build/migration-reports")
        ));
    }

    @Test
    void defaultsToFullMigrationModeAndRequireEmptyDataOnlyPolicy() {
        MapConfigurationPropertySource source = basePropertySource();

        MigrationProperties props = new Binder(source)
            .bind("migration", Bindable.of(MigrationProperties.class))
            .orElseThrow(() -> new IllegalStateException("Migration properties did not bind"));

        assertThat(props.mode()).isEqualTo(MigrationMode.FULL);
        assertThat(props.dataOnly().targetDataPolicy()).isEqualTo(TargetDataPolicy.REQUIRE_EMPTY);
        assertThat(props.dataOnly().foreignKeyHandling()).isEqualTo(DataOnlyForeignKeyHandling.DISABLE_REENABLE);
    }

    @Test
    void bindsDataOnlyMigrationMode() {
        MapConfigurationPropertySource source = basePropertySource();
        source.put("migration.mode", "DATA_ONLY");
        source.put("migration.data-only.target-data-policy", "REQUIRE_EMPTY");
        source.put("migration.data-only.foreign-key-handling", "ORDER_ONLY");

        MigrationProperties props = new Binder(source)
            .bind("migration", Bindable.of(MigrationProperties.class))
            .orElseThrow(() -> new IllegalStateException("Migration properties did not bind"));

        assertThat(props.mode()).isEqualTo(MigrationMode.DATA_ONLY);
        assertThat(props.dataOnly().targetDataPolicy()).isEqualTo(TargetDataPolicy.REQUIRE_EMPTY);
        assertThat(props.dataOnly().foreignKeyHandling()).isEqualTo(DataOnlyForeignKeyHandling.ORDER_ONLY);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MigrationProperties.class)
    static class TestConfig {
    }
}

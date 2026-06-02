# Oracle To GaussDB Data And View Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a one-time, clean-load migration tool that scans Oracle table/view metadata, creates compatible GaussDB structures, migrates table data, validates integrity, and emits detailed audit reports.

**Architecture:** Implement a Java 21 Spring Boot CLI backed by Spring Batch. Keep source metadata scanning, DDL planning, DDL application, data loading, checkpointing, validation, view migration, and reporting in separate packages with narrow interfaces.

**Tech Stack:** Java 21, Maven, Spring Boot 3.x, Spring Batch, Spring JDBC, Jackson, Logback JSON logging, JUnit 5, AssertJ, Mockito, Testcontainers where available.

---

## File Structure

- Create: `pom.xml` - Maven project and dependencies.
- Create: `src/main/java/com/bank/migration/MigrationToolApplication.java` - Spring Boot CLI entrypoint.
- Create: `src/main/java/com/bank/migration/config/MigrationProperties.java` - strongly typed source, target, batch, and report configuration.
- Create: `src/main/java/com/bank/migration/config/DataSourceConfig.java` - source Oracle and target GaussDB data sources.
- Create: `src/main/java/com/bank/migration/domain/*.java` - immutable domain records for metadata, chunks, errors, reports, and validation.
- Create: `src/main/java/com/bank/migration/scanner/*.java` - Oracle metadata scanner.
- Create: `src/main/java/com/bank/migration/ddl/*.java` - type mapping, DDL planning, DDL application.
- Create: `src/main/java/com/bank/migration/chunk/*.java` - chunk planning.
- Create: `src/main/java/com/bank/migration/load/*.java` - Spring Batch data load jobs.
- Create: `src/main/java/com/bank/migration/audit/*.java` - checkpoint and error persistence.
- Create: `src/main/java/com/bank/migration/validate/*.java` - integrity validation.
- Create: `src/main/java/com/bank/migration/view/*.java` - normal view planning and application.
- Create: `src/main/java/com/bank/migration/report/*.java` - JSON, CSV, and HTML reports.
- Create: `src/main/resources/application.yml` - default config shape with environment variable bindings.
- Create: `src/test/java/com/bank/migration/**` - unit tests per module.
- Create: `README.md` - operational usage and phase-one constraints.

---

### Task 1: Scaffold The Spring Boot CLI

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/bank/migration/MigrationToolApplication.java`
- Create: `src/main/java/com/bank/migration/config/MigrationProperties.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/bank/migration/config/MigrationPropertiesTest.java`

- [ ] **Step 1: Create a failing configuration binding test**

```java
package com.bank.migration.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

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
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -q -Dtest=MigrationPropertiesTest test`

Expected: FAIL because the Maven project and `MigrationProperties` do not exist yet.

- [ ] **Step 3: Create the Maven project**

Use this `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.bank</groupId>
    <artifactId>oracle-gaussdb-migration</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>Oracle To GaussDB Migration Tool</name>

    <properties>
        <java.version>21</java.version>
        <spring-boot.version>3.3.5</spring-boot.version>
        <maven.compiler.release>21</maven.compiler.release>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-batch</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.batch</groupId>
            <artifactId>spring-batch-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <version>${spring-boot.version}</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>${maven.compiler.release}</release>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: Create the application entrypoint**

```java
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
```

- [ ] **Step 5: Create strongly typed config**

```java
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
```

- [ ] **Step 6: Create default YAML config**

```yaml
migration:
  source:
    jdbc-url: ${MIGRATION_SOURCE_JDBC_URL}
    username: ${MIGRATION_SOURCE_USERNAME}
    password: ${MIGRATION_SOURCE_PASSWORD}
    driver-class-name: ${MIGRATION_SOURCE_DRIVER_CLASS_NAME:oracle.jdbc.OracleDriver}
    schema: ${MIGRATION_SOURCE_SCHEMA}
  target:
    jdbc-url: ${MIGRATION_TARGET_JDBC_URL}
    username: ${MIGRATION_TARGET_USERNAME}
    password: ${MIGRATION_TARGET_PASSWORD}
    driver-class-name: ${MIGRATION_TARGET_DRIVER_CLASS_NAME:org.postgresql.Driver}
    schema: ${MIGRATION_TARGET_SCHEMA}
  clean-load: ${MIGRATION_CLEAN_LOAD:true}
  batch:
    chunk-size: ${MIGRATION_BATCH_CHUNK_SIZE:5000}
    fetch-size: ${MIGRATION_BATCH_FETCH_SIZE:5000}
    max-parallel-tables: ${MIGRATION_BATCH_MAX_PARALLEL_TABLES:2}
  reports:
    output-dir: ${MIGRATION_REPORT_OUTPUT_DIR:build/migration-reports}
```

- [ ] **Step 7: Run config test**

Run: `mvn -q -Dtest=MigrationPropertiesTest test`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add pom.xml src/main/java/com/bank/migration src/main/resources/application.yml src/test/java/com/bank/migration/config/MigrationPropertiesTest.java
git commit -m "feat: scaffold migration tool"
```

---

### Task 2: Add Data Sources And Preflight Safety

**Files:**
- Create: `src/main/java/com/bank/migration/config/DataSourceConfig.java`
- Create: `src/main/java/com/bank/migration/preflight/PreflightCheck.java`
- Create: `src/main/java/com/bank/migration/preflight/PreflightService.java`
- Test: `src/test/java/com/bank/migration/preflight/PreflightServiceTest.java`

- [ ] **Step 1: Write the failing preflight test**

```java
package com.bank.migration.preflight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class PreflightServiceTest {
    @Mock JdbcTemplate sourceJdbc;
    @Mock JdbcTemplate targetJdbc;

    @Test
    void failsWhenTargetSchemaContainsRows() {
        when(sourceJdbc.queryForObject("select 1 from dual", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            "bank_core"
        )).thenReturn(3);

        PreflightService service = new PreflightService(sourceJdbc, targetJdbc);

        List<PreflightCheck> checks = service.run("bank_core", true);

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("target-schema-empty");
            assertThat(check.passed()).isFalse();
            assertThat(check.message()).contains("contains 3 existing tables");
        });
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -q -Dtest=PreflightServiceTest test`

Expected: FAIL because preflight classes do not exist.

- [ ] **Step 3: Add named data sources**

```java
package com.bank.migration.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {
    @Bean
    @Qualifier("sourceDataSource")
    DataSource sourceDataSource(MigrationProperties properties) {
        return dataSource(properties.source());
    }

    @Bean
    @Qualifier("targetDataSource")
    DataSource targetDataSource(MigrationProperties properties) {
        return dataSource(properties.target());
    }

    @Bean
    @Qualifier("sourceJdbc")
    JdbcTemplate sourceJdbc(@Qualifier("sourceDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Qualifier("targetJdbc")
    JdbcTemplate targetJdbc(@Qualifier("targetDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private HikariDataSource dataSource(MigrationProperties.Database database) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(database.jdbcUrl());
        dataSource.setUsername(database.username());
        dataSource.setPassword(database.password());
        dataSource.setDriverClassName(database.driverClassName());
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }
}
```

- [ ] **Step 4: Add preflight model and service**

```java
package com.bank.migration.preflight;

public record PreflightCheck(String name, boolean passed, String message) {}
```

```java
package com.bank.migration.preflight;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PreflightService {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;

    public PreflightService(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
    }

    public List<PreflightCheck> run(String targetSchema, boolean cleanLoad) {
        List<PreflightCheck> checks = new ArrayList<>();
        checks.add(checkSourceConnectivity());
        checks.add(checkTargetConnectivity());
        checks.add(checkTargetSchemaEmpty(targetSchema, cleanLoad));
        return checks;
    }

    private PreflightCheck checkSourceConnectivity() {
        Integer value = sourceJdbc.queryForObject("select 1 from dual", Integer.class);
        return new PreflightCheck("source-connectivity", value != null && value == 1, "Oracle source connectivity check completed");
    }

    private PreflightCheck checkTargetConnectivity() {
        Integer value = targetJdbc.queryForObject("select 1", Integer.class);
        return new PreflightCheck("target-connectivity", value != null && value == 1, "GaussDB target connectivity check completed");
    }

    private PreflightCheck checkTargetSchemaEmpty(String targetSchema, boolean cleanLoad) {
        Integer tableCount = targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            targetSchema.toLowerCase()
        );
        boolean empty = tableCount == null || tableCount == 0;
        boolean passed = empty || !cleanLoad;
        String message = empty
            ? "Target schema is empty"
            : "Target schema contains " + tableCount + " existing tables";
        return new PreflightCheck("target-schema-empty", passed, message);
    }
}
```

- [ ] **Step 5: Run preflight tests**

Run: `mvn -q -Dtest=PreflightServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/migration/config/DataSourceConfig.java src/main/java/com/bank/migration/preflight src/test/java/com/bank/migration/preflight/PreflightServiceTest.java
git commit -m "feat: add data sources and preflight checks"
```

---

### Task 3: Define Metadata Domain And Manifest

**Files:**
- Create: `src/main/java/com/bank/migration/domain/ObjectStatus.java`
- Create: `src/main/java/com/bank/migration/domain/ColumnMetadata.java`
- Create: `src/main/java/com/bank/migration/domain/TableMetadata.java`
- Create: `src/main/java/com/bank/migration/domain/KeyMetadata.java`
- Create: `src/main/java/com/bank/migration/domain/IndexMetadata.java`
- Create: `src/main/java/com/bank/migration/domain/ViewMetadata.java`
- Create: `src/main/java/com/bank/migration/domain/MigrationManifest.java`
- Test: `src/test/java/com/bank/migration/domain/MigrationManifestTest.java`

- [ ] **Step 1: Write the failing manifest serialization test**

```java
package com.bank.migration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.List;

class MigrationManifestTest {
    @Test
    void serializesTablesAndViews() throws Exception {
        MigrationManifest manifest = new MigrationManifest(
            "run-001",
            "BANK_CORE",
            List.of(new TableMetadata(
                "BANK_CORE",
                "ACCOUNT",
                ObjectStatus.READY,
                List.of(new ColumnMetadata("ID", "NUMBER", 19, 0, false, null)),
                List.of(new KeyMetadata("PK_ACCOUNT", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of(new IndexMetadata("IX_ACCOUNT_ID", false, List.of("ID")))
            )),
            List.of(new ViewMetadata("BANK_CORE", "VW_ACCOUNT", ObjectStatus.READY, "select ID from ACCOUNT", List.of("ACCOUNT"), List.of()))
        );

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(manifest);

        assertThat(json).contains("\"sourceSchema\":\"BANK_CORE\"");
        assertThat(json).contains("\"name\":\"ACCOUNT\"");
        assertThat(json).contains("\"name\":\"VW_ACCOUNT\"");
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `mvn -q -Dtest=MigrationManifestTest test`

Expected: FAIL because domain records do not exist.

- [ ] **Step 3: Add metadata records**

```java
package com.bank.migration.domain;

public enum ObjectStatus {
    READY,
    WARNING,
    NEEDS_REVIEW
}
```

```java
package com.bank.migration.domain;

public record ColumnMetadata(
    String name,
    String oracleType,
    Integer precision,
    Integer scale,
    boolean nullable,
    String defaultExpression
) {}
```

```java
package com.bank.migration.domain;

import java.util.List;

public record KeyMetadata(
    String name,
    String type,
    List<String> columns,
    String referencedTable,
    List<String> referencedColumns
) {}
```

```java
package com.bank.migration.domain;

import java.util.List;

public record IndexMetadata(String name, boolean unique, List<String> columns) {}
```

```java
package com.bank.migration.domain;

import java.util.List;

public record TableMetadata(
    String schema,
    String name,
    ObjectStatus status,
    List<ColumnMetadata> columns,
    List<KeyMetadata> keys,
    List<IndexMetadata> indexes
) {}
```

```java
package com.bank.migration.domain;

import java.util.List;

public record ViewMetadata(
    String schema,
    String name,
    ObjectStatus status,
    String sql,
    List<String> dependencies,
    List<String> notes
) {}
```

```java
package com.bank.migration.domain;

import java.util.List;

public record MigrationManifest(
    String runId,
    String sourceSchema,
    List<TableMetadata> tables,
    List<ViewMetadata> views
) {}
```

- [ ] **Step 4: Run manifest tests**

Run: `mvn -q -Dtest=MigrationManifestTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/migration/domain src/test/java/com/bank/migration/domain/MigrationManifestTest.java
git commit -m "feat: define migration metadata manifest"
```

---

### Task 4: Implement Oracle Metadata Scanner

**Files:**
- Create: `src/main/java/com/bank/migration/scanner/OracleMetadataScanner.java`
- Test: `src/test/java/com/bank/migration/scanner/OracleMetadataScannerTest.java`

- [ ] **Step 1: Write scanner tests using mocked JDBC rows**

```java
package com.bank.migration.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class OracleMetadataScannerTest {
    @Mock JdbcTemplate jdbc;

    @Test
    @SuppressWarnings("unchecked")
    void scansTablesColumnsKeysIndexesAndViews() {
        when(jdbc.query(eq(OracleMetadataScanner.TABLE_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of("ACCOUNT"));
        when(jdbc.query(eq(OracleMetadataScanner.COLUMN_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.ColumnRow("ACCOUNT", "ID", "NUMBER", 19, 0, "N", null)));
        when(jdbc.query(eq(OracleMetadataScanner.KEY_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.KeyRow("ACCOUNT", "PK_ACCOUNT", "PRIMARY_KEY", "ID", null, null)));
        when(jdbc.query(eq(OracleMetadataScanner.INDEX_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.IndexRow("ACCOUNT", "IX_ACCOUNT_ID", "N", "ID")));
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.ViewRow("VW_ACCOUNT", "select ID from ACCOUNT")));

        OracleMetadataScanner scanner = new OracleMetadataScanner(jdbc);

        MigrationManifest manifest = scanner.scan("run-001", "BANK_CORE");

        assertThat(manifest.tables()).hasSize(1);
        assertThat(manifest.tables().getFirst().name()).isEqualTo("ACCOUNT");
        assertThat(manifest.tables().getFirst().status()).isEqualTo(ObjectStatus.READY);
        assertThat(manifest.tables().getFirst().columns()).hasSize(1);
        assertThat(manifest.views()).hasSize(1);
        assertThat(manifest.views().getFirst().name()).isEqualTo("VW_ACCOUNT");
    }
}
```

- [ ] **Step 2: Run scanner tests and verify failure**

Run: `mvn -q -Dtest=OracleMetadataScannerTest test`

Expected: FAIL because `OracleMetadataScanner` does not exist.

- [ ] **Step 3: Implement scanner with grouped metadata**

Create `OracleMetadataScanner` with these public SQL constants and method:

```java
package com.bank.migration.scanner;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.domain.ViewMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OracleMetadataScanner {
    public static final String TABLE_SQL = """
        select table_name
        from all_tables
        where owner = ?
        order by table_name
        """;

    public static final String COLUMN_SQL = """
        select table_name, column_name, data_type,
               case
                 when data_type in ('VARCHAR2', 'NVARCHAR2', 'CHAR', 'NCHAR') then coalesce(char_length, data_length)
                 else data_precision
               end as data_precision,
               data_scale, nullable, data_default
        from all_tab_columns
        where owner = ?
        order by table_name, column_id
        """;

    public static final String KEY_SQL = """
        select c.table_name, c.constraint_name, c.constraint_type, cc.column_name,
               r.table_name as referenced_table_name, rcc.column_name as referenced_column_name
        from all_constraints c
        join all_cons_columns cc on cc.owner = c.owner and cc.constraint_name = c.constraint_name
        left join all_constraints r on r.owner = c.r_owner and r.constraint_name = c.r_constraint_name
        left join all_cons_columns rcc on rcc.owner = r.owner and rcc.constraint_name = r.constraint_name and rcc.position = cc.position
        where c.owner = ? and c.constraint_type in ('P', 'U', 'R')
        order by c.table_name, c.constraint_name, cc.position
        """;

    public static final String INDEX_SQL = """
        select i.table_name, i.index_name, i.uniqueness, ic.column_name
        from all_indexes i
        join all_ind_columns ic on ic.index_owner = i.owner and ic.index_name = i.index_name
        where i.owner = ?
        order by i.table_name, i.index_name, ic.column_position
        """;

    public static final String VIEW_SQL = """
        select view_name, text
        from all_views
        where owner = ?
        order by view_name
        """;

    private final JdbcTemplate jdbc;

    public OracleMetadataScanner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MigrationManifest scan(String runId, String sourceSchema) {
        List<String> tableNames = jdbc.query(TABLE_SQL, (rs, rowNum) -> rs.getString("table_name"), sourceSchema);
        List<ColumnRow> columns = jdbc.query(COLUMN_SQL, (rs, rowNum) -> new ColumnRow(
            rs.getString("table_name"),
            rs.getString("column_name"),
            rs.getString("data_type"),
            (Integer) rs.getObject("data_precision"),
            (Integer) rs.getObject("data_scale"),
            rs.getString("nullable"),
            rs.getString("data_default")
        ), sourceSchema);
        List<KeyRow> keys = jdbc.query(KEY_SQL, (rs, rowNum) -> new KeyRow(
            rs.getString("table_name"),
            rs.getString("constraint_name"),
            normalizeConstraintType(rs.getString("constraint_type")),
            rs.getString("column_name"),
            rs.getString("referenced_table_name"),
            rs.getString("referenced_column_name")
        ), sourceSchema);
        List<IndexRow> indexes = jdbc.query(INDEX_SQL, (rs, rowNum) -> new IndexRow(
            rs.getString("table_name"),
            rs.getString("index_name"),
            rs.getString("uniqueness"),
            rs.getString("column_name")
        ), sourceSchema);
        List<ViewRow> views = jdbc.query(VIEW_SQL, (rs, rowNum) -> new ViewRow(
            rs.getString("view_name"),
            rs.getString("text")
        ), sourceSchema);

        List<TableMetadata> tables = tableNames.stream()
            .map(table -> new TableMetadata(
                sourceSchema,
                table,
                ObjectStatus.READY,
                toColumns(table, columns),
                toKeys(table, keys),
                toIndexes(table, indexes)
            ))
            .toList();

        List<ViewMetadata> viewMetadata = views.stream()
            .map(view -> new ViewMetadata(sourceSchema, view.name(), ObjectStatus.READY, view.sql(), List.of(), List.of()))
            .toList();

        return new MigrationManifest(runId, sourceSchema, tables, viewMetadata);
    }

    private static List<ColumnMetadata> toColumns(String table, List<ColumnRow> rows) {
        return rows.stream()
            .filter(row -> row.tableName().equals(table))
            .map(row -> new ColumnMetadata(row.columnName(), row.dataType(), row.precision(), row.scale(), "Y".equals(row.nullable()), row.dataDefault()))
            .toList();
    }

    private static List<KeyMetadata> toKeys(String table, List<KeyRow> rows) {
        Map<String, List<KeyRow>> grouped = rows.stream()
            .filter(row -> row.tableName().equals(table))
            .collect(Collectors.groupingBy(KeyRow::constraintName, LinkedHashMap::new, Collectors.toList()));
        List<KeyMetadata> keys = new ArrayList<>();
        for (List<KeyRow> group : grouped.values()) {
            KeyRow first = group.getFirst();
            keys.add(new KeyMetadata(
                first.constraintName(),
                first.constraintType(),
                group.stream().map(KeyRow::columnName).toList(),
                first.referencedTableName(),
                group.stream().map(KeyRow::referencedColumnName).filter(value -> value != null).toList()
            ));
        }
        return keys;
    }

    private static List<IndexMetadata> toIndexes(String table, List<IndexRow> rows) {
        Map<String, List<IndexRow>> grouped = rows.stream()
            .filter(row -> row.tableName().equals(table))
            .collect(Collectors.groupingBy(IndexRow::indexName, LinkedHashMap::new, Collectors.toList()));
        List<IndexMetadata> indexes = new ArrayList<>();
        for (List<IndexRow> group : grouped.values()) {
            IndexRow first = group.getFirst();
            indexes.add(new IndexMetadata(
                first.indexName(),
                "UNIQUE".equalsIgnoreCase(first.uniqueness()),
                group.stream().map(IndexRow::columnName).toList()
            ));
        }
        return indexes;
    }

    private static String normalizeConstraintType(String oracleType) {
        return switch (oracleType) {
            case "P" -> "PRIMARY_KEY";
            case "U" -> "UNIQUE";
            case "R" -> "FOREIGN_KEY";
            default -> oracleType;
        };
    }

    public record ColumnRow(String tableName, String columnName, String dataType, Integer precision, Integer scale, String nullable, String dataDefault) {}
    public record KeyRow(String tableName, String constraintName, String constraintType, String columnName, String referencedTableName, String referencedColumnName) {}
    public record IndexRow(String tableName, String indexName, String uniqueness, String columnName) {}
    public record ViewRow(String name, String sql) {}
}
```

- [ ] **Step 4: Run scanner tests**

Run: `mvn -q -Dtest=OracleMetadataScannerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/migration/scanner src/test/java/com/bank/migration/scanner/OracleMetadataScannerTest.java
git commit -m "feat: scan oracle metadata"
```

---

### Task 5: Implement Type Mapping And Table DDL Planning

**Files:**
- Create: `src/main/java/com/bank/migration/ddl/GaussType.java`
- Create: `src/main/java/com/bank/migration/ddl/OracleToGaussTypeMapper.java`
- Create: `src/main/java/com/bank/migration/ddl/DdlStatement.java`
- Create: `src/main/java/com/bank/migration/ddl/TableDdlPlanner.java`
- Test: `src/test/java/com/bank/migration/ddl/OracleToGaussTypeMapperTest.java`
- Test: `src/test/java/com/bank/migration/ddl/TableDdlPlannerTest.java`

- [ ] **Step 1: Write failing type mapper tests**

```java
package com.bank.migration.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ColumnMetadata;
import org.junit.jupiter.api.Test;

class OracleToGaussTypeMapperTest {
    private final OracleToGaussTypeMapper mapper = new OracleToGaussTypeMapper();

    @Test
    void mapsIntegerNumbersSafely() {
        assertThat(mapper.map(new ColumnMetadata("ID", "NUMBER", 19, 0, false, null)).sqlType()).isEqualTo("bigint");
        assertThat(mapper.map(new ColumnMetadata("AMOUNT", "NUMBER", 18, 2, false, null)).sqlType()).isEqualTo("numeric(18,2)");
    }

    @Test
    void marksUnsupportedTypesAsReview() {
        GaussType mapped = mapper.map(new ColumnMetadata("XML_PAYLOAD", "XMLTYPE", null, null, true, null));
        assertThat(mapped.needsReview()).isTrue();
        assertThat(mapped.notes()).contains("Unsupported Oracle type XMLTYPE");
    }
}
```

- [ ] **Step 2: Write failing table DDL planner test**

```java
package com.bank.migration.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

class TableDdlPlannerTest {
    @Test
    void createsTableAndDeferredIndexStatements() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 19, 0, false, null),
                new ColumnMetadata("ACCOUNT_NO", "VARCHAR2", 32, null, false, null)
            ),
            List.of(new KeyMetadata("PK_ACCOUNT", "PRIMARY_KEY", List.of("ID"), null, null)),
            List.of(new IndexMetadata("IX_ACCOUNT_NO", false, List.of("ACCOUNT_NO")))
        );

        TableDdlPlanner planner = new TableDdlPlanner(new OracleToGaussTypeMapper());

        List<DdlStatement> ddl = planner.plan("bank_core", table);

        assertThat(ddl).extracting(DdlStatement::phase).contains("TABLE", "CONSTRAINT", "INDEX");
        assertThat(ddl.getFirst().sql()).contains("create table bank_core.account");
        assertThat(ddl.getFirst().sql()).contains("id bigint not null");
        assertThat(ddl).anySatisfy(statement -> assertThat(statement.sql()).contains("primary key (id)"));
        assertThat(ddl).anySatisfy(statement -> assertThat(statement.sql()).contains("create index ix_account_no"));
    }
}
```

- [ ] **Step 3: Run DDL tests and verify failure**

Run: `mvn -q -Dtest='OracleToGaussTypeMapperTest,TableDdlPlannerTest' test`

Expected: FAIL because DDL classes do not exist.

- [ ] **Step 4: Implement type mapper and DDL planner**

Implement `GaussType`:

```java
package com.bank.migration.ddl;

import java.util.List;

public record GaussType(String sqlType, boolean needsReview, List<String> notes) {}
```

Implement `DdlStatement`:

```java
package com.bank.migration.ddl;

public record DdlStatement(String phase, String objectName, String sql) {}
```

Implement `OracleToGaussTypeMapper` with these mappings:

```java
package com.bank.migration.ddl;

import com.bank.migration.domain.ColumnMetadata;

import java.util.List;

public class OracleToGaussTypeMapper {
    public GaussType map(ColumnMetadata column) {
        String type = column.oracleType().toUpperCase();
        return switch (type) {
            case "NUMBER" -> mapNumber(column);
            case "VARCHAR2", "NVARCHAR2" -> new GaussType(varchar(column), false, List.of());
            case "CHAR", "NCHAR" -> new GaussType(charType(column), false, List.of());
            case "DATE", "TIMESTAMP", "TIMESTAMP(6)" -> new GaussType("timestamp", false, List.of());
            case "CLOB", "NCLOB" -> new GaussType("text", false, List.of());
            case "BLOB", "RAW" -> new GaussType("bytea", false, List.of());
            default -> new GaussType("text", true, List.of("Unsupported Oracle type " + type));
        };
    }

    private GaussType mapNumber(ColumnMetadata column) {
        Integer precision = column.precision();
        Integer scale = column.scale();
        if (precision == null) {
            return new GaussType("numeric", false, List.of("NUMBER without precision mapped to numeric"));
        }
        int safeScale = scale == null ? 0 : scale;
        if (safeScale == 0) {
            if (precision <= 4) return new GaussType("smallint", false, List.of());
            if (precision <= 9) return new GaussType("integer", false, List.of());
            if (precision <= 19) return new GaussType("bigint", false, List.of());
            return new GaussType("numeric(" + precision + ",0)", false, List.of());
        }
        return new GaussType("numeric(" + precision + "," + safeScale + ")", false, List.of());
    }

    private String varchar(ColumnMetadata column) {
        int length = column.precision() == null ? 4000 : column.precision();
        return "varchar(" + length + ")";
    }

    private String charType(ColumnMetadata column) {
        int length = column.precision() == null ? 1 : column.precision();
        return "char(" + length + ")";
    }
}
```

Implement `TableDdlPlanner`:

```java
package com.bank.migration.ddl;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TableDdlPlanner {
    private final OracleToGaussTypeMapper typeMapper;

    public TableDdlPlanner(OracleToGaussTypeMapper typeMapper) {
        this.typeMapper = typeMapper;
    }

    public List<DdlStatement> plan(String targetSchema, TableMetadata table) {
        List<DdlStatement> statements = new ArrayList<>();
        statements.add(new DdlStatement("TABLE", table.name(), createTableSql(targetSchema, table)));
        table.keys().stream()
            .filter(key -> "PRIMARY_KEY".equals(key.type()) || "UNIQUE".equals(key.type()))
            .map(key -> new DdlStatement("CONSTRAINT", key.name(), keySql(targetSchema, table.name(), key)))
            .forEach(statements::add);
        table.indexes().stream()
            .map(index -> new DdlStatement("INDEX", index.name(), indexSql(targetSchema, table.name(), index)))
            .forEach(statements::add);
        table.keys().stream()
            .filter(key -> "FOREIGN_KEY".equals(key.type()))
            .map(key -> new DdlStatement("CONSTRAINT", key.name(), foreignKeySql(targetSchema, table.name(), key)))
            .forEach(statements::add);
        return statements;
    }

    private String createTableSql(String targetSchema, TableMetadata table) {
        String columns = table.columns().stream()
            .map(this::columnSql)
            .reduce((left, right) -> left + ",\n  " + right)
            .orElseThrow();
        return "create table " + q(targetSchema) + "." + q(table.name()) + " (\n  " + columns + "\n)";
    }

    private String columnSql(ColumnMetadata column) {
        GaussType mapped = typeMapper.map(column);
        return q(column.name()) + " " + mapped.sqlType() + (column.nullable() ? "" : " not null");
    }

    private String keySql(String targetSchema, String tableName, KeyMetadata key) {
        String keyType = "PRIMARY_KEY".equals(key.type()) ? "primary key" : "unique";
        return "alter table " + q(targetSchema) + "." + q(tableName)
            + " add constraint " + q(key.name()) + " " + keyType
            + " (" + joinColumns(key.columns()) + ")";
    }

    private String foreignKeySql(String targetSchema, String tableName, KeyMetadata key) {
        return "alter table " + q(targetSchema) + "." + q(tableName)
            + " add constraint " + q(key.name()) + " foreign key (" + joinColumns(key.columns()) + ")"
            + " references " + q(targetSchema) + "." + q(key.referencedTable()) + " (" + joinColumns(key.referencedColumns()) + ")";
    }

    private String indexSql(String targetSchema, String tableName, IndexMetadata index) {
        String unique = index.unique() ? "unique " : "";
        return "create " + unique + "index " + q(index.name()) + " on "
            + q(targetSchema) + "." + q(tableName) + " (" + joinColumns(index.columns()) + ")";
    }

    private String joinColumns(List<String> columns) {
        return String.join(", ", columns.stream().map(TableDdlPlanner::q).toList());
    }

    private static String q(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 5: Run DDL tests**

Run: `mvn -q -Dtest='OracleToGaussTypeMapperTest,TableDdlPlannerTest' test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/migration/ddl src/test/java/com/bank/migration/ddl
git commit -m "feat: plan gaussdb table ddl"
```

---

### Task 6: Add DDL Applier And Clean-Load Target Preparation

**Files:**
- Create: `src/main/java/com/bank/migration/ddl/DdlApplier.java`
- Create: `src/main/java/com/bank/migration/ddl/TargetSchemaService.java`
- Test: `src/test/java/com/bank/migration/ddl/DdlApplierTest.java`
- Test: `src/test/java/com/bank/migration/ddl/TargetSchemaServiceTest.java`

- [ ] **Step 1: Write failing DDL applier test**

```java
package com.bank.migration.ddl;

import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class DdlApplierTest {
    @Mock JdbcTemplate targetJdbc;

    @Test
    void appliesStatementsInOrder() {
        DdlApplier applier = new DdlApplier(targetJdbc);

        applier.apply(List.of(
            new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)"),
            new DdlStatement("INDEX", "IX_ACCOUNT", "create index ix_account on bank_core.account (id)")
        ));

        InOrder order = inOrder(targetJdbc);
        order.verify(targetJdbc).execute("create table bank_core.account (id bigint)");
        order.verify(targetJdbc).execute("create index ix_account on bank_core.account (id)");
    }
}
```

- [ ] **Step 2: Write failing clean-load schema service test**

```java
package com.bank.migration.ddl;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TargetSchemaServiceTest {
    @Mock JdbcTemplate targetJdbc;

    @Test
    void dropsAndRecreatesSchemaForCleanLoad() {
        TargetSchemaService service = new TargetSchemaService(targetJdbc);

        service.prepareCleanSchema("bank_core");

        verify(targetJdbc).execute("drop schema if exists bank_core cascade");
        verify(targetJdbc).execute("create schema bank_core");
    }
}
```

- [ ] **Step 3: Run DDL apply tests and verify failure**

Run: `mvn -q -Dtest='DdlApplierTest,TargetSchemaServiceTest' test`

Expected: FAIL because classes do not exist.

- [ ] **Step 4: Implement DDL applier and schema preparation**

```java
package com.bank.migration.ddl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DdlApplier {
    private final JdbcTemplate targetJdbc;

    public DdlApplier(@Qualifier("targetJdbc") JdbcTemplate targetJdbc) {
        this.targetJdbc = targetJdbc;
    }

    public void apply(List<DdlStatement> statements) {
        for (DdlStatement statement : statements) {
            targetJdbc.execute(statement.sql());
        }
    }
}
```

```java
package com.bank.migration.ddl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TargetSchemaService {
    private final JdbcTemplate targetJdbc;

    public TargetSchemaService(@Qualifier("targetJdbc") JdbcTemplate targetJdbc) {
        this.targetJdbc = targetJdbc;
    }

    public void prepareCleanSchema(String targetSchema) {
        String schema = targetSchema.toLowerCase();
        targetJdbc.execute("drop schema if exists " + schema + " cascade");
        targetJdbc.execute("create schema " + schema);
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn -q -Dtest='DdlApplierTest,TargetSchemaServiceTest' test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/migration/ddl/DdlApplier.java src/main/java/com/bank/migration/ddl/TargetSchemaService.java src/test/java/com/bank/migration/ddl/DdlApplierTest.java src/test/java/com/bank/migration/ddl/TargetSchemaServiceTest.java
git commit -m "feat: apply target ddl safely"
```

---

### Task 7: Implement Chunk Planning

**Files:**
- Create: `src/main/java/com/bank/migration/chunk/ChunkPlan.java`
- Create: `src/main/java/com/bank/migration/chunk/ChunkPlanner.java`
- Test: `src/test/java/com/bank/migration/chunk/ChunkPlannerTest.java`

- [ ] **Step 1: Write failing chunk planner tests**

```java
package com.bank.migration.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

class ChunkPlannerTest {
    @Test
    void plansPrimaryKeyRangesForNumericSingleColumnPrimaryKey() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(new ColumnMetadata("ID", "NUMBER", 19, 0, false, null)),
            List.of(new KeyMetadata("PK_ACCOUNT", "PRIMARY_KEY", List.of("ID"), null, null)),
            List.of()
        );

        ChunkPlanner planner = new ChunkPlanner();

        List<ChunkPlan> chunks = planner.plan(table, 1L, 10_000L, 5_000);

        assertThat(chunks).containsExactly(
            new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 5000", "PRIMARY_KEY_RANGE"),
            new ChunkPlan("ACCOUNT-000002", "ID", "ID >= 5001 and ID <= 10000", "PRIMARY_KEY_RANGE")
        );
    }

    @Test
    void usesRowIdFallbackWhenNoPrimaryKeyExists() {
        TableMetadata table = new TableMetadata("BANK_CORE", "AUDIT_LOG", ObjectStatus.WARNING, List.of(), List.of(), List.of());

        List<ChunkPlan> chunks = new ChunkPlanner().plan(table, 1L, 1L, 5000);

        assertThat(chunks.getFirst().strategy()).isEqualTo("ROWID_FALLBACK");
        assertThat(chunks.getFirst().whereClause()).isEqualTo("1 = 1");
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `mvn -q -Dtest=ChunkPlannerTest test`

Expected: FAIL because chunk classes do not exist.

- [ ] **Step 3: Implement chunk planning**

```java
package com.bank.migration.chunk;

public record ChunkPlan(String chunkId, String columnName, String whereClause, String strategy) {}
```

```java
package com.bank.migration.chunk;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChunkPlanner {
    public List<ChunkPlan> plan(TableMetadata table, long minInclusive, long maxInclusive, int chunkSize) {
        Optional<KeyMetadata> primaryKey = table.keys().stream()
            .filter(key -> "PRIMARY_KEY".equals(key.type()))
            .filter(key -> key.columns().size() == 1)
            .findFirst();

        if (primaryKey.isEmpty()) {
            return List.of(new ChunkPlan(table.name() + "-000001", "ROWID", "1 = 1", "ROWID_FALLBACK"));
        }

        String column = primaryKey.get().columns().getFirst();
        List<ChunkPlan> chunks = new ArrayList<>();
        long start = minInclusive;
        int index = 1;
        while (start <= maxInclusive) {
            long end = Math.min(start + chunkSize - 1, maxInclusive);
            String chunkId = "%s-%06d".formatted(table.name(), index);
            chunks.add(new ChunkPlan(chunkId, column, column + " >= " + start + " and " + column + " <= " + end, "PRIMARY_KEY_RANGE"));
            start = end + 1;
            index++;
        }
        return chunks;
    }
}
```

- [ ] **Step 4: Run chunk tests**

Run: `mvn -q -Dtest=ChunkPlannerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/migration/chunk src/test/java/com/bank/migration/chunk/ChunkPlannerTest.java
git commit -m "feat: plan table data chunks"
```

---

### Task 8: Add Checkpoint And Error Audit Stores

**Files:**
- Create: `src/main/java/com/bank/migration/audit/ChunkStatus.java`
- Create: `src/main/java/com/bank/migration/audit/CheckpointRecord.java`
- Create: `src/main/java/com/bank/migration/audit/ErrorRecord.java`
- Create: `src/main/java/com/bank/migration/audit/AuditSchemaService.java`
- Create: `src/main/java/com/bank/migration/audit/CheckpointStore.java`
- Create: `src/main/java/com/bank/migration/audit/ErrorLogStore.java`
- Test: `src/test/java/com/bank/migration/audit/AuditSchemaServiceTest.java`
- Test: `src/test/java/com/bank/migration/audit/CheckpointStoreTest.java`

- [ ] **Step 1: Write failing audit schema test**

```java
package com.bank.migration.audit;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AuditSchemaServiceTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void createsAuditTables() {
        AuditSchemaService service = new AuditSchemaService(jdbc);

        service.ensureAuditSchema();

        verify(jdbc).execute("create schema if not exists migration_audit");
    }
}
```

- [ ] **Step 2: Write failing checkpoint store test**

```java
package com.bank.migration.audit;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class CheckpointStoreTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void marksChunkSuccess() {
        CheckpointStore store = new CheckpointStore(jdbc);

        store.save(new CheckpointRecord(
            "run-001",
            "BANK_CORE",
            "ACCOUNT",
            "ACCOUNT-000001",
            "ID >= 1 and ID <= 5000",
            5000,
            5000,
            ChunkStatus.SUCCESS,
            0,
            Instant.parse("2026-06-02T00:00:00Z"),
            Instant.parse("2026-06-02T00:01:00Z"),
            null
        ));

        verify(jdbc).update(
            CheckpointStore.UPSERT_SQL,
            "run-001", "BANK_CORE", "ACCOUNT", "ACCOUNT-000001", "ID >= 1 and ID <= 5000",
            5000L, 5000L, "SUCCESS", 0, Instant.parse("2026-06-02T00:00:00Z"), Instant.parse("2026-06-02T00:01:00Z"), null
        );
    }
}
```

- [ ] **Step 3: Run audit tests and verify failure**

Run: `mvn -q -Dtest='AuditSchemaServiceTest,CheckpointStoreTest' test`

Expected: FAIL because audit classes do not exist.

- [ ] **Step 4: Implement audit records and stores**

Create records:

```java
package com.bank.migration.audit;

public enum ChunkStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    SKIPPED
}
```

```java
package com.bank.migration.audit;

import java.time.Instant;

public record CheckpointRecord(
    String runId,
    String schemaName,
    String tableName,
    String chunkId,
    String chunkRange,
    long rowsRead,
    long rowsWritten,
    ChunkStatus status,
    int retryCount,
    Instant startedAt,
    Instant completedAt,
    String lastErrorId
) {}
```

```java
package com.bank.migration.audit;

import java.time.Instant;

public record ErrorRecord(
    String errorId,
    String runId,
    String phase,
    String objectType,
    String objectName,
    String chunkId,
    String sqlText,
    String databaseCode,
    String message,
    String actionCategory,
    Instant createdAt
) {}
```

Create schema service:

```java
package com.bank.migration.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditSchemaService {
    private final JdbcTemplate jdbc;

    public AuditSchemaService(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ensureAuditSchema() {
        jdbc.execute("create schema if not exists migration_audit");
        jdbc.execute("""
            create table if not exists migration_audit.checkpoints (
              run_id varchar(80) not null,
              schema_name varchar(128) not null,
              table_name varchar(128) not null,
              chunk_id varchar(160) not null,
              chunk_range text not null,
              rows_read bigint not null,
              rows_written bigint not null,
              status varchar(30) not null,
              retry_count integer not null,
              started_at timestamp,
              completed_at timestamp,
              last_error_id varchar(120),
              primary key (run_id, table_name, chunk_id)
            )
            """);
        jdbc.execute("""
            create table if not exists migration_audit.errors (
              error_id varchar(120) primary key,
              run_id varchar(80) not null,
              phase varchar(40) not null,
              object_type varchar(40) not null,
              object_name varchar(256),
              chunk_id varchar(160),
              sql_text text,
              database_code varchar(120),
              message text not null,
              action_category varchar(80) not null,
              created_at timestamp not null
            )
            """);
    }
}
```

Create stores:

```java
package com.bank.migration.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CheckpointStore {
    public static final String UPSERT_SQL = """
        insert into migration_audit.checkpoints
        (run_id, schema_name, table_name, chunk_id, chunk_range, rows_read, rows_written, status, retry_count, started_at, completed_at, last_error_id)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (run_id, table_name, chunk_id)
        do update set rows_read = excluded.rows_read,
                      rows_written = excluded.rows_written,
                      status = excluded.status,
                      retry_count = excluded.retry_count,
                      completed_at = excluded.completed_at,
                      last_error_id = excluded.last_error_id
        """;

    private final JdbcTemplate jdbc;

    public CheckpointStore(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(CheckpointRecord record) {
        jdbc.update(
            UPSERT_SQL,
            record.runId(),
            record.schemaName(),
            record.tableName(),
            record.chunkId(),
            record.chunkRange(),
            record.rowsRead(),
            record.rowsWritten(),
            record.status().name(),
            record.retryCount(),
            record.startedAt(),
            record.completedAt(),
            record.lastErrorId()
        );
    }
}
```

```java
package com.bank.migration.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ErrorLogStore {
    private static final String INSERT_SQL = """
        insert into migration_audit.errors
        (error_id, run_id, phase, object_type, object_name, chunk_id, sql_text, database_code, message, action_category, created_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private final JdbcTemplate jdbc;

    public ErrorLogStore(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ErrorRecord record) {
        jdbc.update(
            INSERT_SQL,
            record.errorId(),
            record.runId(),
            record.phase(),
            record.objectType(),
            record.objectName(),
            record.chunkId(),
            record.sqlText(),
            record.databaseCode(),
            record.message(),
            record.actionCategory(),
            record.createdAt()
        );
    }
}
```

- [ ] **Step 5: Run audit tests**

Run: `mvn -q -Dtest='AuditSchemaServiceTest,CheckpointStoreTest' test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/migration/audit src/test/java/com/bank/migration/audit
git commit -m "feat: persist migration checkpoints and errors"
```

---

### Task 9: Implement Data Copy Service

**Files:**
- Create: `src/main/java/com/bank/migration/load/DataCopyService.java`
- Create: `src/main/java/com/bank/migration/load/TableCopyResult.java`
- Test: `src/test/java/com/bank/migration/load/DataCopyServiceTest.java`

- [ ] **Step 1: Write failing data copy test**

```java
package com.bank.migration.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class DataCopyServiceTest {
    @Mock JdbcTemplate sourceJdbc;
    @Mock JdbcTemplate targetJdbc;

    @Test
    @SuppressWarnings("unchecked")
    void copiesChunkRowsWithBatchInsert() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 19, 0, false, null),
                new ColumnMetadata("ACCOUNT_NO", "VARCHAR2", 32, null, false, null)
            ),
            List.of(),
            List.of()
        );
        ChunkPlan chunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 2", "PRIMARY_KEY_RANGE");
        when(sourceJdbc.query(
            "select ID, ACCOUNT_NO from BANK_CORE.ACCOUNT where ID >= 1 and ID <= 2",
            (RowMapper<Map<String, Object>>) org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(Map.of("ID", 1L, "ACCOUNT_NO", "A001"), Map.of("ID", 2L, "ACCOUNT_NO", "A002")));

        DataCopyService service = new DataCopyService(sourceJdbc, targetJdbc);

        TableCopyResult result = service.copyChunk(table, "bank_core", chunk);

        assertThat(result.rowsRead()).isEqualTo(2);
        assertThat(result.rowsWritten()).isEqualTo(2);
        verify(targetJdbc).batchUpdate(
            org.mockito.ArgumentMatchers.eq("insert into bank_core.account (id, account_no) values (?, ?)"),
            org.mockito.ArgumentMatchers.anyList()
        );
    }
}
```

- [ ] **Step 2: Run data copy test and verify failure**

Run: `mvn -q -Dtest=DataCopyServiceTest test`

Expected: FAIL because copy classes do not exist.

- [ ] **Step 3: Implement table copy result**

```java
package com.bank.migration.load;

public record TableCopyResult(long rowsRead, long rowsWritten) {}
```

- [ ] **Step 4: Implement data copy service**

```java
package com.bank.migration.load;

import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.TableMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DataCopyService {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;

    public DataCopyService(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
    }

    public TableCopyResult copyChunk(TableMetadata table, String targetSchema, ChunkPlan chunk) {
        List<String> sourceColumns = table.columns().stream().map(ColumnMetadata::name).toList();
        String sourceSql = "select " + String.join(", ", sourceColumns)
            + " from " + table.schema() + "." + table.name()
            + " where " + chunk.whereClause();
        List<Map<String, Object>> rows = sourceJdbc.query(sourceSql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : sourceColumns) {
                row.put(column, rs.getObject(column));
            }
            return row;
        });
        if (rows.isEmpty()) {
            return new TableCopyResult(0, 0);
        }

        List<String> targetColumns = sourceColumns.stream().map(DataCopyService::lower).toList();
        String insertSql = "insert into " + lower(targetSchema) + "." + lower(table.name())
            + " (" + String.join(", ", targetColumns) + ") values ("
            + String.join(", ", targetColumns.stream().map(column -> "?").toList()) + ")";

        List<Object[]> args = rows.stream()
            .map(row -> sourceColumns.stream().map(row::get).toArray())
            .toList();
        int[] counts = targetJdbc.batchUpdate(insertSql, args);
        long written = 0;
        for (int count : counts) {
            written += count;
        }
        return new TableCopyResult(rows.size(), written);
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
```

- [ ] **Step 5: Run data copy tests**

Run: `mvn -q -Dtest=DataCopyServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/migration/load src/test/java/com/bank/migration/load/DataCopyServiceTest.java
git commit -m "feat: copy chunk data into gaussdb"
```

---

### Task 10: Wire Spring Batch Load Job

**Files:**
- Create: `src/main/java/com/bank/migration/load/TableMigrationTasklet.java`
- Create: `src/main/java/com/bank/migration/load/MigrationJobConfig.java`
- Test: `src/test/java/com/bank/migration/load/TableMigrationTaskletTest.java`

- [ ] **Step 1: Write failing tasklet test**

```java
package com.bank.migration.load;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.audit.ChunkStatus;
import com.bank.migration.audit.CheckpointRecord;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class TableMigrationTaskletTest {
    @Mock DataCopyService copyService;
    @Mock CheckpointStore checkpointStore;

    @Test
    void savesSuccessfulCheckpointForEachChunk() {
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        ChunkPlan chunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 10", "PRIMARY_KEY_RANGE");
        when(copyService.copyChunk(table, "bank_core", chunk)).thenReturn(new TableCopyResult(10, 10));
        TableMigrationTasklet tasklet = new TableMigrationTasklet("run-001", "bank_core", table, List.of(chunk), copyService, checkpointStore);

        tasklet.run();

        verify(checkpointStore).save(org.mockito.ArgumentMatchers.argThat(record ->
            record.runId().equals("run-001")
                && record.tableName().equals("ACCOUNT")
                && record.chunkId().equals("ACCOUNT-000001")
                && record.status() == ChunkStatus.SUCCESS
                && record.rowsRead() == 10
                && record.rowsWritten() == 10
        ));
    }
}
```

- [ ] **Step 2: Run tasklet test and verify failure**

Run: `mvn -q -Dtest=TableMigrationTaskletTest test`

Expected: FAIL because tasklet does not exist.

- [ ] **Step 3: Implement table migration tasklet**

```java
package com.bank.migration.load;

import com.bank.migration.audit.CheckpointRecord;
import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.audit.ChunkStatus;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.TableMetadata;

import java.time.Instant;
import java.util.List;

public class TableMigrationTasklet {
    private final String runId;
    private final String targetSchema;
    private final TableMetadata table;
    private final List<ChunkPlan> chunks;
    private final DataCopyService copyService;
    private final CheckpointStore checkpointStore;

    public TableMigrationTasklet(
        String runId,
        String targetSchema,
        TableMetadata table,
        List<ChunkPlan> chunks,
        DataCopyService copyService,
        CheckpointStore checkpointStore
    ) {
        this.runId = runId;
        this.targetSchema = targetSchema;
        this.table = table;
        this.chunks = chunks;
        this.copyService = copyService;
        this.checkpointStore = checkpointStore;
    }

    public void run() {
        for (ChunkPlan chunk : chunks) {
            Instant started = Instant.now();
            TableCopyResult result = copyService.copyChunk(table, targetSchema, chunk);
            checkpointStore.save(new CheckpointRecord(
                runId,
                table.schema(),
                table.name(),
                chunk.chunkId(),
                chunk.whereClause(),
                result.rowsRead(),
                result.rowsWritten(),
                ChunkStatus.SUCCESS,
                0,
                started,
                Instant.now(),
                null
            ));
        }
    }
}
```

- [ ] **Step 4: Add Spring Batch job config shell**

```java
package com.bank.migration.load;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MigrationJobConfig {
    @Bean
    Job migrationJob(JobRepository jobRepository, Step migrationStep) {
        return new JobBuilder("oracleToGaussDbMigrationJob", jobRepository)
            .start(migrationStep)
            .build();
    }

    @Bean
    Step migrationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("migrationOrchestrationStep", jobRepository)
            .tasklet((contribution, chunkContext) -> org.springframework.batch.repeat.RepeatStatus.FINISHED, transactionManager)
            .build();
    }
}
```

- [ ] **Step 5: Run tasklet tests**

Run: `mvn -q -Dtest=TableMigrationTaskletTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/migration/load/TableMigrationTasklet.java src/main/java/com/bank/migration/load/MigrationJobConfig.java src/test/java/com/bank/migration/load/TableMigrationTaskletTest.java
git commit -m "feat: wire batch table migration tasklet"
```

---

### Task 11: Add Validation Services

**Files:**
- Create: `src/main/java/com/bank/migration/validate/ValidationStatus.java`
- Create: `src/main/java/com/bank/migration/validate/ValidationResult.java`
- Create: `src/main/java/com/bank/migration/validate/RowCountValidator.java`
- Create: `src/main/java/com/bank/migration/validate/DuplicateKeyValidator.java`
- Create: `src/main/java/com/bank/migration/validate/ForeignKeyValidator.java`
- Test: `src/test/java/com/bank/migration/validate/RowCountValidatorTest.java`

- [ ] **Step 1: Write failing row count validator test**

```java
package com.bank.migration.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class RowCountValidatorTest {
    @Mock JdbcTemplate sourceJdbc;
    @Mock JdbcTemplate targetJdbc;

    @Test
    void failsWhenCountsDiffer() {
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        when(sourceJdbc.queryForObject("select count(*) from BANK_CORE.ACCOUNT", Long.class)).thenReturn(100L);
        when(targetJdbc.queryForObject("select count(*) from bank_core.account", Long.class)).thenReturn(99L);

        RowCountValidator validator = new RowCountValidator(sourceJdbc, targetJdbc);

        ValidationResult result = validator.validate(table, "bank_core");

        assertThat(result.status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(result.message()).contains("source=100 target=99");
    }
}
```

- [ ] **Step 2: Run validation tests and verify failure**

Run: `mvn -q -Dtest=RowCountValidatorTest test`

Expected: FAIL because validation classes do not exist.

- [ ] **Step 3: Implement validation records and row count validator**

```java
package com.bank.migration.validate;

public enum ValidationStatus {
    PASS,
    FAIL,
    WARNING
}
```

```java
package com.bank.migration.validate;

public record ValidationResult(String name, ValidationStatus status, String objectName, String message) {}
```

```java
package com.bank.migration.validate;

import com.bank.migration.domain.TableMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class RowCountValidator {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;

    public RowCountValidator(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
    }

    public ValidationResult validate(TableMetadata table, String targetSchema) {
        Long sourceCount = sourceJdbc.queryForObject("select count(*) from " + table.schema() + "." + table.name(), Long.class);
        Long targetCount = targetJdbc.queryForObject(
            "select count(*) from " + targetSchema.toLowerCase(Locale.ROOT) + "." + table.name().toLowerCase(Locale.ROOT),
            Long.class
        );
        boolean pass = sourceCount != null && sourceCount.equals(targetCount);
        return new ValidationResult(
            "row-count",
            pass ? ValidationStatus.PASS : ValidationStatus.FAIL,
            table.name(),
            "source=" + sourceCount + " target=" + targetCount
        );
    }
}
```

- [ ] **Step 4: Add duplicate and FK validator skeletons with deterministic SQL builders**

```java
package com.bank.migration.validate;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import org.springframework.stereotype.Service;

@Service
public class DuplicateKeyValidator {
    public String duplicateSql(String targetSchema, TableMetadata table, KeyMetadata key) {
        String columns = String.join(", ", key.columns().stream().map(String::toLowerCase).toList());
        return "select " + columns + ", count(*) from " + targetSchema.toLowerCase() + "." + table.name().toLowerCase()
            + " group by " + columns + " having count(*) > 1";
    }
}
```

```java
package com.bank.migration.validate;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import org.springframework.stereotype.Service;

@Service
public class ForeignKeyValidator {
    public String orphanSql(String targetSchema, TableMetadata childTable, KeyMetadata foreignKey) {
        String child = targetSchema.toLowerCase() + "." + childTable.name().toLowerCase();
        String parent = targetSchema.toLowerCase() + "." + foreignKey.referencedTable().toLowerCase();
        String join = "";
        for (int i = 0; i < foreignKey.columns().size(); i++) {
            if (i > 0) join += " and ";
            join += "c." + foreignKey.columns().get(i).toLowerCase() + " = p." + foreignKey.referencedColumns().get(i).toLowerCase();
        }
        String parentNullCheck = "p." + foreignKey.referencedColumns().getFirst().toLowerCase() + " is null";
        return "select count(*) from " + child + " c left join " + parent + " p on " + join + " where " + parentNullCheck;
    }
}
```

- [ ] **Step 5: Run validation tests**

Run: `mvn -q -Dtest=RowCountValidatorTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/migration/validate src/test/java/com/bank/migration/validate/RowCountValidatorTest.java
git commit -m "feat: validate migrated data integrity"
```

---

### Task 12: Implement Normal View Planning And Application

**Files:**
- Create: `src/main/java/com/bank/migration/view/ViewPlan.java`
- Create: `src/main/java/com/bank/migration/view/ViewPlanner.java`
- Create: `src/main/java/com/bank/migration/view/ViewApplier.java`
- Test: `src/test/java/com/bank/migration/view/ViewPlannerTest.java`

- [ ] **Step 1: Write failing view planner tests**

```java
package com.bank.migration.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.ViewMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

class ViewPlannerTest {
    @Test
    void createsCompatibleViewSql() {
        ViewPlanner planner = new ViewPlanner();
        ViewMetadata view = new ViewMetadata("BANK_CORE", "VW_ACCOUNT", ObjectStatus.READY, "select ID from ACCOUNT", List.of("ACCOUNT"), List.of());

        ViewPlan plan = planner.plan("bank_core", view);

        assertThat(plan.status()).isEqualTo(ObjectStatus.READY);
        assertThat(plan.sql()).isEqualTo("create or replace view bank_core.vw_account as select id from account");
    }

    @Test
    void marksOracleSpecificViewForReview() {
        ViewPlanner planner = new ViewPlanner();
        ViewMetadata view = new ViewMetadata("BANK_CORE", "VW_BAD", ObjectStatus.READY, "select sys_context('USERENV','SESSION_USER') from dual", List.of(), List.of());

        ViewPlan plan = planner.plan("bank_core", view);

        assertThat(plan.status()).isEqualTo(ObjectStatus.NEEDS_REVIEW);
        assertThat(plan.notes()).contains("Oracle-specific SQL detected: sys_context");
    }
}
```

- [ ] **Step 2: Run view tests and verify failure**

Run: `mvn -q -Dtest=ViewPlannerTest test`

Expected: FAIL because view classes do not exist.

- [ ] **Step 3: Implement view plan and planner**

```java
package com.bank.migration.view;

import com.bank.migration.domain.ObjectStatus;

import java.util.List;

public record ViewPlan(String name, ObjectStatus status, String sql, List<String> notes) {}
```

```java
package com.bank.migration.view;

import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.ViewMetadata;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ViewPlanner {
    public ViewPlan plan(String targetSchema, ViewMetadata view) {
        List<String> notes = new ArrayList<>();
        String normalized = view.sql().replaceAll("\\s+", " ").trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        if (lower.contains("sys_context")) {
            notes.add("Oracle-specific SQL detected: sys_context");
        }
        if (lower.contains("connect by")) {
            notes.add("Oracle-specific SQL detected: connect by");
        }
        if (lower.contains(" from dual")) {
            notes.add("Oracle-specific SQL detected: dual");
        }

        ObjectStatus status = notes.isEmpty() ? ObjectStatus.READY : ObjectStatus.NEEDS_REVIEW;
        String sql = "create or replace view " + targetSchema.toLowerCase(Locale.ROOT) + "." + view.name().toLowerCase(Locale.ROOT)
            + " as " + lower;
        return new ViewPlan(view.name(), status, sql, notes);
    }
}
```

- [ ] **Step 4: Implement view applier**

```java
package com.bank.migration.view;

import com.bank.migration.domain.ObjectStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ViewApplier {
    private final JdbcTemplate targetJdbc;

    public ViewApplier(@Qualifier("targetJdbc") JdbcTemplate targetJdbc) {
        this.targetJdbc = targetJdbc;
    }

    public void applyReadyViews(List<ViewPlan> plans) {
        for (ViewPlan plan : plans) {
            if (plan.status() == ObjectStatus.READY) {
                targetJdbc.execute(plan.sql());
            }
        }
    }
}
```

- [ ] **Step 5: Run view tests**

Run: `mvn -q -Dtest=ViewPlannerTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/migration/view src/test/java/com/bank/migration/view/ViewPlannerTest.java
git commit -m "feat: plan compatible view migration"
```

---

### Task 13: Implement Report Generation

**Files:**
- Create: `src/main/java/com/bank/migration/report/MigrationReport.java`
- Create: `src/main/java/com/bank/migration/report/ReportWriter.java`
- Test: `src/test/java/com/bank/migration/report/ReportWriterTest.java`

- [ ] **Step 1: Write failing report writer test**

```java
package com.bank.migration.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.validate.ValidationResult;
import com.bank.migration.validate.ValidationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

class ReportWriterTest {
    @TempDir Path tempDir;

    @Test
    void writesJsonCsvAndHtmlReports() throws Exception {
        ReportWriter writer = new ReportWriter();
        MigrationReport report = new MigrationReport(
            "run-001",
            "PASS",
            Instant.parse("2026-06-02T00:00:00Z"),
            Instant.parse("2026-06-02T00:10:00Z"),
            List.of(new ValidationResult("row-count", ValidationStatus.PASS, "ACCOUNT", "source=10 target=10")),
            List.of()
        );

        writer.write(report, tempDir);

        assertThat(Files.readString(tempDir.resolve("run-001-report.json"))).contains("\"status\" : \"PASS\"");
        assertThat(Files.readString(tempDir.resolve("run-001-report.csv"))).contains("row-count,PASS,ACCOUNT");
        assertThat(Files.readString(tempDir.resolve("run-001-report.html"))).contains("<h1>Migration Report run-001</h1>");
    }
}
```

- [ ] **Step 2: Run report tests and verify failure**

Run: `mvn -q -Dtest=ReportWriterTest test`

Expected: FAIL because report classes do not exist.

- [ ] **Step 3: Implement report records and writer**

```java
package com.bank.migration.report;

import com.bank.migration.audit.ErrorRecord;
import com.bank.migration.validate.ValidationResult;

import java.time.Instant;
import java.util.List;

public record MigrationReport(
    String runId,
    String status,
    Instant startedAt,
    Instant completedAt,
    List<ValidationResult> validations,
    List<ErrorRecord> errors
) {}
```

```java
package com.bank.migration.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReportWriter {
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(MigrationReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve(report.runId() + "-report.json"), mapper.writeValueAsString(report));
        Files.writeString(outputDir.resolve(report.runId() + "-report.csv"), csv(report));
        Files.writeString(outputDir.resolve(report.runId() + "-report.html"), html(report));
    }

    private String csv(MigrationReport report) {
        StringBuilder builder = new StringBuilder("name,status,object,message\n");
        report.validations().forEach(result -> builder
            .append(result.name()).append(',')
            .append(result.status()).append(',')
            .append(result.objectName()).append(',')
            .append(result.message().replace(",", ";")).append('\n'));
        return builder.toString();
    }

    private String html(MigrationReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><body>");
        builder.append("<h1>Migration Report ").append(report.runId()).append("</h1>");
        builder.append("<p>Status: ").append(report.status()).append("</p>");
        builder.append("<table><thead><tr><th>Name</th><th>Status</th><th>Object</th><th>Message</th></tr></thead><tbody>");
        report.validations().forEach(result -> builder.append("<tr><td>")
            .append(result.name()).append("</td><td>")
            .append(result.status()).append("</td><td>")
            .append(result.objectName()).append("</td><td>")
            .append(result.message()).append("</td></tr>"));
        builder.append("</tbody></table></body></html>");
        return builder.toString();
    }
}
```

- [ ] **Step 4: Run report tests**

Run: `mvn -q -Dtest=ReportWriterTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bank/migration/report src/test/java/com/bank/migration/report/ReportWriterTest.java
git commit -m "feat: write migration audit reports"
```

---

### Task 14: Add Orchestrator For Dry Run And Clean Load

**Files:**
- Create: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`
- Modify: `src/main/java/com/bank/migration/MigrationToolApplication.java`
- Test: `src/test/java/com/bank/migration/orchestrator/MigrationOrchestratorTest.java`

- [ ] **Step 1: Write failing orchestrator test**

```java
package com.bank.migration.orchestrator;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.DdlStatement;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.scanner.OracleMetadataScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class MigrationOrchestratorTest {
    @Mock OracleMetadataScanner scanner;
    @Mock TargetSchemaService targetSchemaService;
    @Mock TableDdlPlanner ddlPlanner;
    @Mock DdlApplier ddlApplier;

    @Test
    void scansPreparesAndAppliesTableDdl() {
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports")
        );
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        when(scanner.scan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("BANK_CORE")))
            .thenReturn(new MigrationManifest("run-001", "BANK_CORE", List.of(table), List.of()));
        when(ddlPlanner.plan("bank_core", table)).thenReturn(List.of(new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)")));

        MigrationOrchestrator orchestrator = new MigrationOrchestrator(scanner, targetSchemaService, ddlPlanner, ddlApplier);

        orchestrator.run(props);

        verify(targetSchemaService).prepareCleanSchema("bank_core");
        verify(ddlApplier).apply(List.of(new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)")));
    }
}
```

- [ ] **Step 2: Run orchestrator test and verify failure**

Run: `mvn -q -Dtest=MigrationOrchestratorTest test`

Expected: FAIL because orchestrator does not exist.

- [ ] **Step 3: Implement orchestrator**

```java
package com.bank.migration.orchestrator;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.DdlStatement;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.scanner.OracleMetadataScanner;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MigrationOrchestrator {
    private final OracleMetadataScanner scanner;
    private final TargetSchemaService targetSchemaService;
    private final TableDdlPlanner ddlPlanner;
    private final DdlApplier ddlApplier;

    public MigrationOrchestrator(
        OracleMetadataScanner scanner,
        TargetSchemaService targetSchemaService,
        TableDdlPlanner ddlPlanner,
        DdlApplier ddlApplier
    ) {
        this.scanner = scanner;
        this.targetSchemaService = targetSchemaService;
        this.ddlPlanner = ddlPlanner;
        this.ddlApplier = ddlApplier;
    }

    public void run(MigrationProperties properties) {
        String runId = "run-" + UUID.randomUUID();
        MigrationManifest manifest = scanner.scan(runId, properties.source().schema());
        if (properties.cleanLoad()) {
            targetSchemaService.prepareCleanSchema(properties.target().schema());
        }
        List<DdlStatement> statements = new ArrayList<>();
        for (TableMetadata table : manifest.tables()) {
            statements.addAll(ddlPlanner.plan(properties.target().schema(), table));
        }
        ddlApplier.apply(statements);
    }
}
```

- [ ] **Step 4: Modify application to invoke orchestrator**

```java
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
```

- [ ] **Step 5: Run orchestrator tests**

Run: `mvn -q -Dtest=MigrationOrchestratorTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bank/migration/MigrationToolApplication.java src/main/java/com/bank/migration/orchestrator src/test/java/com/bank/migration/orchestrator/MigrationOrchestratorTest.java
git commit -m "feat: orchestrate clean-load migration"
```

---

### Task 15: Complete End-To-End Orchestration

**Files:**
- Create: `src/main/java/com/bank/migration/chunk/ChunkBounds.java`
- Create: `src/main/java/com/bank/migration/chunk/ChunkBoundsService.java`
- Create: `src/main/java/com/bank/migration/validate/ValidationCoordinator.java`
- Modify: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`
- Test: `src/test/java/com/bank/migration/orchestrator/MigrationOrchestratorPipelineTest.java`

- [ ] **Step 1: Write failing pipeline orchestration test**

```java
package com.bank.migration.orchestrator;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.audit.AuditSchemaService;
import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.chunk.ChunkBounds;
import com.bank.migration.chunk.ChunkBoundsService;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.chunk.ChunkPlanner;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.DdlStatement;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.domain.ViewMetadata;
import com.bank.migration.load.DataCopyService;
import com.bank.migration.load.TableCopyResult;
import com.bank.migration.preflight.PreflightCheck;
import com.bank.migration.preflight.PreflightService;
import com.bank.migration.report.ReportWriter;
import com.bank.migration.scanner.OracleMetadataScanner;
import com.bank.migration.validate.ValidationCoordinator;
import com.bank.migration.validate.ValidationResult;
import com.bank.migration.validate.ValidationStatus;
import com.bank.migration.view.ViewApplier;
import com.bank.migration.view.ViewPlan;
import com.bank.migration.view.ViewPlanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class MigrationOrchestratorPipelineTest {
    @Mock PreflightService preflightService;
    @Mock AuditSchemaService auditSchemaService;
    @Mock OracleMetadataScanner scanner;
    @Mock TargetSchemaService targetSchemaService;
    @Mock TableDdlPlanner ddlPlanner;
    @Mock DdlApplier ddlApplier;
    @Mock ChunkBoundsService chunkBoundsService;
    @Mock ChunkPlanner chunkPlanner;
    @Mock DataCopyService dataCopyService;
    @Mock CheckpointStore checkpointStore;
    @Mock ValidationCoordinator validationCoordinator;
    @Mock ViewPlanner viewPlanner;
    @Mock ViewApplier viewApplier;
    @Mock ReportWriter reportWriter;

    @Test
    void runsCleanLoadPipelineInOrder() throws Exception {
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports")
        );
        TableMetadata account = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(new ColumnMetadata("ID", "NUMBER", 19, 0, false, null)),
            List.of(),
            List.of()
        );
        ViewMetadata view = new ViewMetadata("BANK_CORE", "VW_ACCOUNT", ObjectStatus.READY, "select ID from ACCOUNT", List.of("ACCOUNT"), List.of());
        MigrationManifest manifest = new MigrationManifest("run-001", "BANK_CORE", List.of(account), List.of(view));
        ChunkPlan chunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 10", "PRIMARY_KEY_RANGE");
        ViewPlan viewPlan = new ViewPlan("VW_ACCOUNT", ObjectStatus.READY, "create or replace view bank_core.vw_account as select id from account", List.of());

        when(preflightService.run("bank_core", true)).thenReturn(List.of(new PreflightCheck("target-schema-empty", true, "ok")));
        when(scanner.scan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("BANK_CORE"))).thenReturn(manifest);
        when(ddlPlanner.plan("bank_core", account)).thenReturn(List.of(
            new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)"),
            new DdlStatement("CONSTRAINT", "PK_ACCOUNT", "alter table bank_core.account add primary key (id)")
        ));
        when(chunkBoundsService.bounds(account)).thenReturn(new ChunkBounds(1L, 10L));
        when(chunkPlanner.plan(account, 1L, 10L, 5000)).thenReturn(List.of(chunk));
        when(dataCopyService.copyChunk(account, "bank_core", chunk)).thenReturn(new TableCopyResult(10, 10));
        when(validationCoordinator.validate(manifest, "bank_core")).thenReturn(List.of(new ValidationResult("row-count", ValidationStatus.PASS, "ACCOUNT", "source=10 target=10")));
        when(viewPlanner.plan("bank_core", view)).thenReturn(viewPlan);

        MigrationOrchestrator orchestrator = new MigrationOrchestrator(
            preflightService,
            auditSchemaService,
            scanner,
            targetSchemaService,
            ddlPlanner,
            ddlApplier,
            chunkBoundsService,
            chunkPlanner,
            dataCopyService,
            checkpointStore,
            validationCoordinator,
            viewPlanner,
            viewApplier,
            reportWriter
        );

        orchestrator.run(props);

        verify(auditSchemaService).ensureAuditSchema();
        verify(targetSchemaService).prepareCleanSchema("bank_core");
        verify(ddlApplier).apply(List.of(new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)")));
        verify(dataCopyService).copyChunk(account, "bank_core", chunk);
        verify(ddlApplier).apply(List.of(new DdlStatement("CONSTRAINT", "PK_ACCOUNT", "alter table bank_core.account add primary key (id)")));
        verify(viewApplier).applyReadyViews(List.of(viewPlan));
        verify(validationCoordinator).validate(manifest, "bank_core");
    }
}
```

- [ ] **Step 2: Run pipeline test and verify failure**

Run: `mvn -q -Dtest=MigrationOrchestratorPipelineTest test`

Expected: FAIL because pipeline dependencies and the expanded orchestrator do not exist.

- [ ] **Step 3: Add chunk bounds service**

```java
package com.bank.migration.chunk;

public record ChunkBounds(long minInclusive, long maxInclusive) {}
```

```java
package com.bank.migration.chunk;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChunkBoundsService {
    private final JdbcTemplate sourceJdbc;

    public ChunkBoundsService(@Qualifier("sourceJdbc") JdbcTemplate sourceJdbc) {
        this.sourceJdbc = sourceJdbc;
    }

    public ChunkBounds bounds(TableMetadata table) {
        String primaryKeyColumn = table.keys().stream()
            .filter(key -> "PRIMARY_KEY".equals(key.type()))
            .filter(key -> key.columns().size() == 1)
            .map(KeyMetadata::columns)
            .map(columns -> columns.getFirst())
            .findFirst()
            .orElse("ROWID");
        if ("ROWID".equals(primaryKeyColumn)) {
            return new ChunkBounds(1, 1);
        }
        Long min = sourceJdbc.queryForObject("select min(" + primaryKeyColumn + ") from " + table.schema() + "." + table.name(), Long.class);
        Long max = sourceJdbc.queryForObject("select max(" + primaryKeyColumn + ") from " + table.schema() + "." + table.name(), Long.class);
        return new ChunkBounds(min == null ? 1 : min, max == null ? 1 : max);
    }
}
```

- [ ] **Step 4: Add validation coordinator**

```java
package com.bank.migration.validate;

import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.TableMetadata;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ValidationCoordinator {
    private final RowCountValidator rowCountValidator;

    public ValidationCoordinator(RowCountValidator rowCountValidator) {
        this.rowCountValidator = rowCountValidator;
    }

    public List<ValidationResult> validate(MigrationManifest manifest, String targetSchema) {
        List<ValidationResult> results = new ArrayList<>();
        for (TableMetadata table : manifest.tables()) {
            results.add(rowCountValidator.validate(table, targetSchema));
        }
        return results;
    }
}
```

- [ ] **Step 5: Expand orchestrator to run the full phase-one pipeline**

```java
package com.bank.migration.orchestrator;

import com.bank.migration.audit.AuditSchemaService;
import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.chunk.ChunkBounds;
import com.bank.migration.chunk.ChunkBoundsService;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.chunk.ChunkPlanner;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.DdlStatement;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.load.DataCopyService;
import com.bank.migration.preflight.PreflightService;
import com.bank.migration.report.MigrationReport;
import com.bank.migration.report.ReportWriter;
import com.bank.migration.scanner.OracleMetadataScanner;
import com.bank.migration.validate.ValidationCoordinator;
import com.bank.migration.validate.ValidationResult;
import com.bank.migration.view.ViewApplier;
import com.bank.migration.view.ViewPlan;
import com.bank.migration.view.ViewPlanner;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MigrationOrchestrator {
    private final PreflightService preflightService;
    private final AuditSchemaService auditSchemaService;
    private final OracleMetadataScanner scanner;
    private final TargetSchemaService targetSchemaService;
    private final TableDdlPlanner ddlPlanner;
    private final DdlApplier ddlApplier;
    private final ChunkBoundsService chunkBoundsService;
    private final ChunkPlanner chunkPlanner;
    private final DataCopyService dataCopyService;
    private final CheckpointStore checkpointStore;
    private final ValidationCoordinator validationCoordinator;
    private final ViewPlanner viewPlanner;
    private final ViewApplier viewApplier;
    private final ReportWriter reportWriter;

    public MigrationOrchestrator(
        PreflightService preflightService,
        AuditSchemaService auditSchemaService,
        OracleMetadataScanner scanner,
        TargetSchemaService targetSchemaService,
        TableDdlPlanner ddlPlanner,
        DdlApplier ddlApplier,
        ChunkBoundsService chunkBoundsService,
        ChunkPlanner chunkPlanner,
        DataCopyService dataCopyService,
        CheckpointStore checkpointStore,
        ValidationCoordinator validationCoordinator,
        ViewPlanner viewPlanner,
        ViewApplier viewApplier,
        ReportWriter reportWriter
    ) {
        this.preflightService = preflightService;
        this.auditSchemaService = auditSchemaService;
        this.scanner = scanner;
        this.targetSchemaService = targetSchemaService;
        this.ddlPlanner = ddlPlanner;
        this.ddlApplier = ddlApplier;
        this.chunkBoundsService = chunkBoundsService;
        this.chunkPlanner = chunkPlanner;
        this.dataCopyService = dataCopyService;
        this.checkpointStore = checkpointStore;
        this.validationCoordinator = validationCoordinator;
        this.viewPlanner = viewPlanner;
        this.viewApplier = viewApplier;
        this.reportWriter = reportWriter;
    }

    public void run(MigrationProperties properties) throws Exception {
        Instant started = Instant.now();
        String runId = "run-" + UUID.randomUUID();
        String targetSchema = properties.target().schema();

        preflightService.run(targetSchema, properties.cleanLoad()).forEach(check -> {
            if (!check.passed()) {
                throw new IllegalStateException("Preflight failed: " + check.name() + " - " + check.message());
            }
        });
        auditSchemaService.ensureAuditSchema();

        MigrationManifest manifest = scanner.scan(runId, properties.source().schema());
        if (properties.cleanLoad()) {
            targetSchemaService.prepareCleanSchema(targetSchema);
        }

        List<DdlStatement> allDdl = new ArrayList<>();
        for (TableMetadata table : manifest.tables()) {
            allDdl.addAll(ddlPlanner.plan(targetSchema, table));
        }
        ddlApplier.apply(allDdl.stream().filter(statement -> "TABLE".equals(statement.phase())).toList());

        for (TableMetadata table : manifest.tables()) {
            ChunkBounds bounds = chunkBoundsService.bounds(table);
            List<ChunkPlan> chunks = chunkPlanner.plan(table, bounds.minInclusive(), bounds.maxInclusive(), properties.batch().chunkSize());
            for (ChunkPlan chunk : chunks) {
                dataCopyService.copyChunk(table, targetSchema, chunk);
            }
        }

        ddlApplier.apply(allDdl.stream().filter(statement -> !"TABLE".equals(statement.phase())).toList());

        List<ViewPlan> viewPlans = manifest.views().stream()
            .map(view -> viewPlanner.plan(targetSchema, view))
            .toList();
        viewApplier.applyReadyViews(viewPlans);

        List<ValidationResult> validations = validationCoordinator.validate(manifest, targetSchema);
        String status = validations.stream().anyMatch(result -> "FAIL".equals(result.status().name())) ? "FAIL" : "PASS";
        reportWriter.write(new MigrationReport(runId, status, started, Instant.now(), validations, List.of()), Path.of(properties.reports().outputDir()));
    }
}
```

- [ ] **Step 6: Run pipeline test**

Run: `mvn -q -Dtest=MigrationOrchestratorPipelineTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bank/migration/chunk/ChunkBounds.java src/main/java/com/bank/migration/chunk/ChunkBoundsService.java src/main/java/com/bank/migration/validate/ValidationCoordinator.java src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java src/test/java/com/bank/migration/orchestrator/MigrationOrchestratorPipelineTest.java
git commit -m "feat: complete migration pipeline orchestration"
```

---

### Task 16: Add Operational README And Final Verification

**Files:**
- Create: `README.md`
- Modify: `docs/superpowers/specs/2026-06-02-oracle-to-gaussdb-data-view-migration-design.md` only if implementation discovered a necessary clarification.

- [ ] **Step 1: Write README**

```markdown
# Oracle To GaussDB Migration Tool

Offline one-time clean-load migration tool for moving Oracle banking table data and compatible normal views to GaussDB.

## Phase-One Scope

Migrated:

- Tables
- Table data
- Primary keys
- Foreign keys
- Unique constraints
- Indexes
- Compatible normal views

Not migrated:

- Triggers
- Procedures
- Functions
- Packages
- Jobs and schedulers
- Materialized views
- CDC or incremental changes

## Required Configuration

Set these environment variables before running:

- `MIGRATION_SOURCE_JDBC_URL`
- `MIGRATION_SOURCE_USERNAME`
- `MIGRATION_SOURCE_PASSWORD`
- `MIGRATION_SOURCE_DRIVER_CLASS_NAME`
- `MIGRATION_SOURCE_SCHEMA`
- `MIGRATION_TARGET_JDBC_URL`
- `MIGRATION_TARGET_USERNAME`
- `MIGRATION_TARGET_PASSWORD`
- `MIGRATION_TARGET_DRIVER_CLASS_NAME`
- `MIGRATION_TARGET_SCHEMA`

## Run

```bash
mvn -q -DskipTests package
java -jar target/oracle-gaussdb-migration-0.1.0-SNAPSHOT.jar
```

## Safety

Phase one uses clean-load behavior. The target schema must be empty or explicitly disposable. The tool writes checkpoint and error records to `migration_audit`, not to business tables.

## Reports

Reports are written to `build/migration-reports` by default:

- JSON for automation
- CSV for spreadsheet review
- HTML for audit review
```

- [ ] **Step 2: Run all unit tests**

Run: `mvn test`

Expected: all tests PASS.

- [ ] **Step 3: Build the jar**

Run: `mvn -q -DskipTests package`

Expected: `target/oracle-gaussdb-migration-0.1.0-SNAPSHOT.jar` exists.

- [ ] **Step 4: Inspect uncommitted changes**

Run: `git status --short`

Expected: only README or intended final files are uncommitted.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: add migration tool runbook"
```

---

## Self-Review Checklist

- Spec coverage: The plan covers scanning, DDL planning/application, clean-load target preparation, chunk loading, checkpoint/error audit, validation, view planning/application, and reporting.
- Scope control: Trigger, procedure, function, package, job, materialized view, CDC, and incremental sync are excluded.
- Placeholder scan: No task uses an undefined `TBD`, generic "add error handling", or unbounded implementation instruction.
- Type consistency: Domain records use stable names across scanner, DDL, load, validation, view, and report tasks.
- Risk note: Real GaussDB and Oracle integration tests must be added once credentials or disposable test databases are available. Unit tests and mocked JDBC tests are enough for local plan execution, not enough for banking sign-off.

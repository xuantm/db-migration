# Data-Only Migration Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a safe `DATA_ONLY` mode that copies Oracle table data into an already-created GaussDB/openGauss schema without applying table DDL, indexes, constraints, or views, while optionally disabling and re-enabling target FK triggers around the load.

**Architecture:** Keep the current `FULL` migration pipeline as the default path. Add mode-aware configuration, target-schema compatibility checks, FK trigger management, table load ordering, and data-only validation. The data-only path still scans Oracle metadata, applies exclusions, runs readiness checks, chunks and copies table data, writes audit/report artifacts, and validates row counts/FKs; it skips all DDL and view application phases.

**Tech Stack:** Java 21, Spring Boot configuration properties, Spring JDBC, JUnit 5, Mockito, AssertJ, openGauss/PostgreSQL catalog SQL.

---

## Design Decisions

- `migration.mode=FULL` remains the default to preserve current behavior.
- `migration.mode=DATA_ONLY` means target schema objects already exist and are owned by the DBA-provided Gauss DDL.
- `clean-load` does not mean data-only. Data-only must be an explicit mode because `clean-load=false` currently still allows DDL/view execution.
- Initial data-only policy is `REQUIRE_EMPTY`: every included target table must be empty before load. This avoids accidental append and duplicate-data risk.
- FK handling defaults to `DISABLE_REENABLE`: disable target table triggers before data load, always attempt to re-enable in `finally`, then run explicit FK validation.
- `ORDER_ONLY` is supported as a lower-privilege option. It loads parent tables before child tables and blocks cycles.
- `DBA_MANAGED` is supported when a DBA disables/enables FK triggers outside the tool. The tool still validates target FKs after load.
- Target FK validation must use the actual target catalog, not only source Oracle FK metadata, because the user has a canonical target Gauss DDL.

## Files

- Modify: `src/main/java/com/bank/migration/config/MigrationProperties.java`
- Create: `src/main/java/com/bank/migration/config/MigrationMode.java`
- Create: `src/main/java/com/bank/migration/config/TargetDataPolicy.java`
- Create: `src/main/java/com/bank/migration/config/DataOnlyForeignKeyHandling.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/java/com/bank/migration/preflight/PreflightService.java`
- Create: `src/main/java/com/bank/migration/dataonly/DataOnlyTargetReadinessService.java`
- Create: `src/main/java/com/bank/migration/dataonly/DataOnlyTargetColumn.java`
- Create: `src/main/java/com/bank/migration/dataonly/ForeignKeyTriggerManager.java`
- Create: `src/main/java/com/bank/migration/dataonly/TableLoadOrderPlanner.java`
- Create: `src/main/java/com/bank/migration/dataonly/TargetForeignKeyMetadata.java`
- Create: `src/main/java/com/bank/migration/dataonly/TargetForeignKeyScanner.java`
- Create: `src/main/java/com/bank/migration/dataonly/TargetForeignKeyValidator.java`
- Modify: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`
- Modify: `src/main/java/com/bank/migration/report/MigrationReport.java`
- Modify: `src/main/java/com/bank/migration/report/ReportWriter.java`
- Test: `src/test/java/com/bank/migration/config/MigrationPropertiesTest.java`
- Test: `src/test/java/com/bank/migration/preflight/PreflightServiceTest.java`
- Test: `src/test/java/com/bank/migration/dataonly/DataOnlyTargetReadinessServiceTest.java`
- Test: `src/test/java/com/bank/migration/dataonly/ForeignKeyTriggerManagerTest.java`
- Test: `src/test/java/com/bank/migration/dataonly/TableLoadOrderPlannerTest.java`
- Test: `src/test/java/com/bank/migration/dataonly/TargetForeignKeyValidatorTest.java`
- Test: `src/test/java/com/bank/migration/orchestrator/MigrationOrchestratorPipelineTest.java`
- Test: `src/test/java/com/bank/migration/report/ReportWriterTest.java`
- Docs: `README.md`

## Task 1: Add Mode and Data-Only Configuration

**Files:**
- Create: `src/main/java/com/bank/migration/config/MigrationMode.java`
- Create: `src/main/java/com/bank/migration/config/TargetDataPolicy.java`
- Create: `src/main/java/com/bank/migration/config/DataOnlyForeignKeyHandling.java`
- Modify: `src/main/java/com/bank/migration/config/MigrationProperties.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/bank/migration/config/MigrationPropertiesTest.java`

- [ ] **Step 1: Write config binding tests**

Add these tests to `MigrationPropertiesTest`:

```java
@Test
void defaultsToFullMigrationModeAndRequireEmptyDataOnlyPolicy() {
    MapConfigurationPropertySource source = basePropertySource();

    MigrationProperties props = new Binder(source)
        .bind("migration", Bindable.of(MigrationProperties.class))
        .orElseThrow();

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
        .orElseThrow();

    assertThat(props.mode()).isEqualTo(MigrationMode.DATA_ONLY);
    assertThat(props.dataOnly().targetDataPolicy()).isEqualTo(TargetDataPolicy.REQUIRE_EMPTY);
    assertThat(props.dataOnly().foreignKeyHandling()).isEqualTo(DataOnlyForeignKeyHandling.ORDER_ONLY);
}
```

If `basePropertySource()` does not exist, extract the repeated required migration properties in `MigrationPropertiesTest` into a private helper returning `MapConfigurationPropertySource`.

- [ ] **Step 2: Run the config tests and confirm failure**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=MigrationPropertiesTest' test
```

Expected: compile failure because `MigrationMode`, `TargetDataPolicy`, `DataOnlyForeignKeyHandling`, and `MigrationProperties.dataOnly()` do not exist.

- [ ] **Step 3: Add enums**

Create `src/main/java/com/bank/migration/config/MigrationMode.java`:

```java
package com.bank.migration.config;

public enum MigrationMode {
    FULL,
    DATA_ONLY
}
```

Create `src/main/java/com/bank/migration/config/TargetDataPolicy.java`:

```java
package com.bank.migration.config;

public enum TargetDataPolicy {
    REQUIRE_EMPTY
}
```

Create `src/main/java/com/bank/migration/config/DataOnlyForeignKeyHandling.java`:

```java
package com.bank.migration.config;

public enum DataOnlyForeignKeyHandling {
    DISABLE_REENABLE,
    ORDER_ONLY,
    DBA_MANAGED
}
```

- [ ] **Step 4: Extend `MigrationProperties`**

Add fields to the `MigrationProperties` record:

```java
MigrationMode mode,
@Valid DataOnly dataOnly,
```

Default them in the compact constructor:

```java
if (mode == null) {
    mode = MigrationMode.FULL;
}
if (dataOnly == null) {
    dataOnly = new DataOnly(TargetDataPolicy.REQUIRE_EMPTY, DataOnlyForeignKeyHandling.DISABLE_REENABLE);
}
```

Add this nested record:

```java
public record DataOnly(
    TargetDataPolicy targetDataPolicy,
    DataOnlyForeignKeyHandling foreignKeyHandling
) {
    public DataOnly {
        if (targetDataPolicy == null) {
            targetDataPolicy = TargetDataPolicy.REQUIRE_EMPTY;
        }
        if (foreignKeyHandling == null) {
            foreignKeyHandling = DataOnlyForeignKeyHandling.DISABLE_REENABLE;
        }
    }
}
```

Update every convenience constructor in `MigrationProperties` to pass `MigrationMode.FULL` and the default `DataOnly` record.

- [ ] **Step 5: Update `application.yml`**

Add:

```yaml
migration:
  mode: ${MIGRATION_MODE:FULL}
  data-only:
    target-data-policy: ${MIGRATION_DATA_ONLY_TARGET_DATA_POLICY:REQUIRE_EMPTY}
    foreign-key-handling: ${MIGRATION_DATA_ONLY_FOREIGN_KEY_HANDLING:DISABLE_REENABLE}
```

Place these under the existing `migration:` root without duplicating the root key.

- [ ] **Step 6: Verify Task 1**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=MigrationPropertiesTest' test
```

Expected: `BUILD SUCCESS`.

## Task 2: Make Preflight Mode-Aware

**Files:**
- Modify: `src/main/java/com/bank/migration/preflight/PreflightService.java`
- Test: `src/test/java/com/bank/migration/preflight/PreflightServiceTest.java`
- Modify call site: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`

- [ ] **Step 1: Write tests for data-only preflight**

Add this test to `PreflightServiceTest`:

```java
@Test
void dataOnlyPreflightSkipsTargetSchemaEmptyCheck() {
    when(sourceJdbc.queryForObject("select 1 from dual", Integer.class)).thenReturn(1);
    when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);

    PreflightService service = new PreflightService(sourceJdbc, targetJdbc, targetRenderer);

    List<PreflightCheck> checks = service.run("bank_core", true, MigrationMode.DATA_ONLY);

    assertThat(checks).extracting(PreflightCheck::name)
        .containsExactly("source-connectivity", "target-connectivity");
    verify(targetJdbc, never()).queryForObject(
        startsWith("select count(*) from information_schema.tables"),
        eq(Integer.class),
        any()
    );
}
```

- [ ] **Step 2: Run test and confirm failure**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=PreflightServiceTest' test
```

Expected: compile failure because `PreflightService.run(String, boolean, MigrationMode)` does not exist.

- [ ] **Step 3: Add overload**

Modify `PreflightService`:

```java
public List<PreflightCheck> run(String targetSchema, boolean cleanLoad) {
    return run(targetSchema, cleanLoad, MigrationMode.FULL);
}

public List<PreflightCheck> run(String targetSchema, boolean cleanLoad, MigrationMode mode) {
    List<PreflightCheck> checks = new ArrayList<>();
    checks.add(safeCheck("source-connectivity", this::checkSourceConnectivity));
    checks.add(safeCheck("target-connectivity", this::checkTargetConnectivity));
    if (mode == MigrationMode.FULL) {
        checks.add(safeCheck("target-schema-empty", () -> checkTargetSchemaEmpty(targetSchema, cleanLoad)));
    }
    return checks;
}
```

Add import:

```java
import com.bank.migration.config.MigrationMode;
```

- [ ] **Step 4: Update orchestrator call**

Change the existing preflight call in `MigrationOrchestrator.run`:

```java
ensurePreflightPassed(preflightService.run(targetSchema, properties.cleanLoad(), properties.mode()));
```

- [ ] **Step 5: Verify Task 2**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=PreflightServiceTest,MigrationOrchestratorPipelineTest' test
```

Expected: `BUILD SUCCESS`.

## Task 3: Add Target Compatibility Checks for Data-Only

**Files:**
- Create: `src/main/java/com/bank/migration/dataonly/DataOnlyTargetColumn.java`
- Create: `src/main/java/com/bank/migration/dataonly/DataOnlyTargetReadinessService.java`
- Test: `src/test/java/com/bank/migration/dataonly/DataOnlyTargetReadinessServiceTest.java`

- [ ] **Step 1: Write target readiness tests**

Create `DataOnlyTargetReadinessServiceTest` with these cases:

```java
@ExtendWith(MockitoExtension.class)
class DataOnlyTargetReadinessServiceTest {
    @Mock JdbcTemplate targetJdbc;
    @Mock IdentifierRenderer targetRenderer;

    @BeforeEach
    void setUp() {
        when(targetRenderer.physicalName(anyString())).thenAnswer(inv -> inv.getArgument(0, String.class).toLowerCase(Locale.ROOT));
        when(targetRenderer.renderQualifiedName(anyString(), anyString())).thenAnswer(inv ->
            inv.getArgument(0, String.class).toLowerCase(Locale.ROOT) + "." + inv.getArgument(1, String.class).toLowerCase(Locale.ROOT)
        );
    }

    @Test
    void passesWhenTargetTableExistsColumnsMatchAndTableIsEmpty() {
        TableMetadata table = table("ACCOUNT", List.of("ID", "BALANCE"));
        when(targetJdbc.queryForObject(contains("information_schema.tables"), eq(Integer.class), eq("bank_core"), eq("account"))).thenReturn(1);
        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("account"))).thenReturn(List.of(
            new DataOnlyTargetColumn("id", false, null, null, null),
            new DataOnlyTargetColumn("balance", true, null, null, null)
        ));
        when(targetJdbc.queryForObject("select count(*) from bank_core.account", Long.class)).thenReturn(0L);

        DataOnlyTargetReadinessService service = new DataOnlyTargetReadinessService(targetJdbc, targetRenderer);

        List<PreflightCheck> checks = service.check(
            new MigrationManifest("run-1", "BANK_CORE", List.of(table), List.of()),
            "BANK_CORE",
            TargetDataPolicy.REQUIRE_EMPTY
        );

        assertThat(checks).allMatch(PreflightCheck::passed);
    }

    @Test
    void failsWhenTargetTableIsMissing() {
        TableMetadata table = table("ACCOUNT", List.of("ID"));
        when(targetJdbc.queryForObject(contains("information_schema.tables"), eq(Integer.class), eq("bank_core"), eq("account"))).thenReturn(0);

        DataOnlyTargetReadinessService service = new DataOnlyTargetReadinessService(targetJdbc, targetRenderer);

        List<PreflightCheck> checks = service.check(
            new MigrationManifest("run-1", "BANK_CORE", List.of(table), List.of()),
            "BANK_CORE",
            TargetDataPolicy.REQUIRE_EMPTY
        );

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("data-only-target-table-ACCOUNT");
            assertThat(check.passed()).isFalse();
            assertThat(check.message()).contains("Target table bank_core.account does not exist");
        });
    }

    @Test
    void failsWhenTargetColumnIsMissing() {
        TableMetadata table = table("ACCOUNT", List.of("ID", "BALANCE"));
        when(targetJdbc.queryForObject(contains("information_schema.tables"), eq(Integer.class), eq("bank_core"), eq("account"))).thenReturn(1);
        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("account"))).thenReturn(List.of(
            new DataOnlyTargetColumn("id", false, null, null, null)
        ));

        DataOnlyTargetReadinessService service = new DataOnlyTargetReadinessService(targetJdbc, targetRenderer);

        List<PreflightCheck> checks = service.check(
            new MigrationManifest("run-1", "BANK_CORE", List.of(table), List.of()),
            "BANK_CORE",
            TargetDataPolicy.REQUIRE_EMPTY
        );

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("data-only-target-columns-ACCOUNT");
            assertThat(check.passed()).isFalse();
            assertThat(check.message()).contains("Missing target columns: balance");
        });
    }

    @Test
    void failsWhenTargetTableIsNotEmptyUnderRequireEmptyPolicy() {
        TableMetadata table = table("ACCOUNT", List.of("ID"));
        when(targetJdbc.queryForObject(contains("information_schema.tables"), eq(Integer.class), eq("bank_core"), eq("account"))).thenReturn(1);
        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("account"))).thenReturn(List.of(
            new DataOnlyTargetColumn("id", false, null, null, null)
        ));
        when(targetJdbc.queryForObject("select count(*) from bank_core.account", Long.class)).thenReturn(7L);

        DataOnlyTargetReadinessService service = new DataOnlyTargetReadinessService(targetJdbc, targetRenderer);

        List<PreflightCheck> checks = service.check(
            new MigrationManifest("run-1", "BANK_CORE", List.of(table), List.of()),
            "BANK_CORE",
            TargetDataPolicy.REQUIRE_EMPTY
        );

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("data-only-target-empty-ACCOUNT");
            assertThat(check.passed()).isFalse();
            assertThat(check.message()).contains("Target table bank_core.account contains 7 rows");
        });
    }

    @Test
    void failsWhenExtraTargetNotNullColumnHasNoDefaultOrGeneration() {
        TableMetadata table = table("ACCOUNT", List.of("ID"));
        when(targetJdbc.queryForObject(contains("information_schema.tables"), eq(Integer.class), eq("bank_core"), eq("account"))).thenReturn(1);
        when(targetJdbc.query(anyString(), any(RowMapper.class), eq("bank_core"), eq("account"))).thenReturn(List.of(
            new DataOnlyTargetColumn("id", false, null, null, null),
            new DataOnlyTargetColumn("branch_code", false, null, null, null)
        ));

        DataOnlyTargetReadinessService service = new DataOnlyTargetReadinessService(targetJdbc, targetRenderer);

        List<PreflightCheck> checks = service.check(
            new MigrationManifest("run-1", "BANK_CORE", List.of(table), List.of()),
            "BANK_CORE",
            TargetDataPolicy.REQUIRE_EMPTY
        );

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("data-only-target-extra-columns-ACCOUNT");
            assertThat(check.passed()).isFalse();
            assertThat(check.message()).contains("Extra NOT NULL target columns without default/generated value: branch_code");
        });
    }

    private static TableMetadata table(String name, List<String> columns) {
        return new TableMetadata(
            "BANK_CORE",
            name,
            ObjectStatus.READY,
            columns.stream().map(col -> new ColumnMetadata(col, "NUMBER", 18, 0, true, null)).toList(),
            List.of(),
            List.of()
        );
    }
}
```

- [ ] **Step 2: Run test and confirm failure**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=DataOnlyTargetReadinessServiceTest' test
```

Expected: compile failure because the `dataonly` classes do not exist.

- [ ] **Step 3: Implement target column record**

Create `DataOnlyTargetColumn`:

```java
package com.bank.migration.dataonly;

public record DataOnlyTargetColumn(
    String name,
    boolean nullable,
    String defaultExpression,
    String identityGeneration,
    String generationExpression
) {
    public boolean canBeOmittedFromInsert() {
        return nullable
            || hasText(defaultExpression)
            || hasText(identityGeneration)
            || hasText(generationExpression);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
```

- [ ] **Step 4: Implement target readiness service**

Create `DataOnlyTargetReadinessService`:

```java
package com.bank.migration.dataonly;

import com.bank.migration.config.TargetDataPolicy;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.preflight.PreflightCheck;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataOnlyTargetReadinessService {
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;

    public DataOnlyTargetReadinessService(
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
    }

    public List<PreflightCheck> check(MigrationManifest manifest, String targetSchema, TargetDataPolicy policy) {
        List<PreflightCheck> checks = new ArrayList<>();
        for (TableMetadata table : manifest.tables()) {
            if (table.status() == ObjectStatus.EXCLUDED) {
                continue;
            }
            checks.addAll(checkTable(table, targetSchema, policy));
        }
        return List.copyOf(checks);
    }

    private List<PreflightCheck> checkTable(TableMetadata table, String targetSchema, TargetDataPolicy policy) {
        List<PreflightCheck> checks = new ArrayList<>();
        String schema = targetRenderer.physicalName(targetSchema);
        String tableName = targetRenderer.physicalName(table.name());

        Integer exists = targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ? and table_name = ?",
            Integer.class,
            schema,
            tableName
        );
        if (exists == null || exists == 0) {
            checks.add(new PreflightCheck(
                "data-only-target-table-" + table.name(),
                false,
                "Target table " + schema + "." + tableName + " does not exist"
            ));
            return checks;
        }
        checks.add(new PreflightCheck("data-only-target-table-" + table.name(), true, "Target table exists"));

        List<DataOnlyTargetColumn> targetColumns = loadColumns(schema, tableName);
        Set<String> targetColumnNames = new LinkedHashSet<>();
        for (DataOnlyTargetColumn column : targetColumns) {
            targetColumnNames.add(column.name().toLowerCase(Locale.ROOT));
        }
        Set<String> sourceColumnNames = new LinkedHashSet<>();
        for (ColumnMetadata column : table.columns()) {
            sourceColumnNames.add(targetRenderer.physicalName(column.name()).toLowerCase(Locale.ROOT));
        }

        List<String> missingColumns = sourceColumnNames.stream()
            .filter(column -> !targetColumnNames.contains(column))
            .toList();
        checks.add(new PreflightCheck(
            "data-only-target-columns-" + table.name(),
            missingColumns.isEmpty(),
            missingColumns.isEmpty() ? "Target columns are compatible" : "Missing target columns: " + String.join(", ", missingColumns)
        ));

        List<String> unsafeExtraColumns = targetColumns.stream()
            .filter(column -> !sourceColumnNames.contains(column.name().toLowerCase(Locale.ROOT)))
            .filter(column -> !column.canBeOmittedFromInsert())
            .map(DataOnlyTargetColumn::name)
            .toList();
        checks.add(new PreflightCheck(
            "data-only-target-extra-columns-" + table.name(),
            unsafeExtraColumns.isEmpty(),
            unsafeExtraColumns.isEmpty()
                ? "No unsafe extra target columns"
                : "Extra NOT NULL target columns without default/generated value: " + String.join(", ", unsafeExtraColumns)
        ));

        if (policy == TargetDataPolicy.REQUIRE_EMPTY) {
            Long count = targetJdbc.queryForObject(
                "select count(*) from " + targetRenderer.renderQualifiedName(targetSchema, table.name()),
                Long.class
            );
            long rows = count == null ? 0L : count;
            checks.add(new PreflightCheck(
                "data-only-target-empty-" + table.name(),
                rows == 0L,
                rows == 0L
                    ? "Target table is empty"
                    : "Target table " + schema + "." + tableName + " contains " + rows + " rows"
            ));
        }

        return checks;
    }

    private List<DataOnlyTargetColumn> loadColumns(String schema, String tableName) {
        return targetJdbc.query(
            """
            select column_name, is_nullable, column_default, identity_generation, generation_expression
            from information_schema.columns
            where table_schema = ? and table_name = ?
            order by ordinal_position
            """,
            (rs, rowNum) -> new DataOnlyTargetColumn(
                rs.getString("column_name"),
                "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                rs.getString("column_default"),
                rs.getString("identity_generation"),
                rs.getString("generation_expression")
            ),
            schema,
            tableName
        );
    }
}
```

- [ ] **Step 5: Verify Task 3**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=DataOnlyTargetReadinessServiceTest' test
```

Expected: `BUILD SUCCESS`.

## Task 4: Add FK Trigger Disable/Re-enable Manager

**Files:**
- Create: `src/main/java/com/bank/migration/dataonly/ForeignKeyTriggerManager.java`
- Test: `src/test/java/com/bank/migration/dataonly/ForeignKeyTriggerManagerTest.java`

- [ ] **Step 1: Write FK trigger manager tests**

Create `ForeignKeyTriggerManagerTest`:

```java
@ExtendWith(MockitoExtension.class)
class ForeignKeyTriggerManagerTest {
    @Mock JdbcTemplate targetJdbc;
    @Mock IdentifierRenderer targetRenderer;

    @BeforeEach
    void setUp() {
        lenient().when(targetRenderer.renderQualifiedName(anyString(), anyString())).thenAnswer(inv ->
            inv.getArgument(0, String.class).toLowerCase(Locale.ROOT) + "." + inv.getArgument(1, String.class).toLowerCase(Locale.ROOT)
        );
    }

    @Test
    void disablesAndEnablesTriggersForIncludedTables() {
        TableMetadata account = table("ACCOUNT");
        TableMetadata tx = table("TXN");
        ForeignKeyTriggerManager manager = new ForeignKeyTriggerManager(targetJdbc, targetRenderer);

        manager.disableAll("BANK_CORE", List.of(account, tx));
        manager.enableAll("BANK_CORE", List.of(account, tx));

        InOrder order = inOrder(targetJdbc);
        order.verify(targetJdbc).execute("alter table bank_core.account disable trigger all");
        order.verify(targetJdbc).execute("alter table bank_core.txn disable trigger all");
        order.verify(targetJdbc).execute("alter table bank_core.account enable trigger all");
        order.verify(targetJdbc).execute("alter table bank_core.txn enable trigger all");
    }

    @Test
    void skipsExcludedTables() {
        TableMetadata excluded = new TableMetadata(
            "BANK_CORE",
            "AUDIT_LOG",
            ObjectStatus.EXCLUDED,
            List.of(),
            List.of(),
            List.of(),
            "Excluded by configuration"
        );
        ForeignKeyTriggerManager manager = new ForeignKeyTriggerManager(targetJdbc, targetRenderer);

        manager.disableAll("BANK_CORE", List.of(excluded));

        verifyNoInteractions(targetJdbc);
    }

    private static TableMetadata table(String name) {
        return new TableMetadata("BANK_CORE", name, ObjectStatus.READY, List.of(), List.of(), List.of());
    }
}
```

- [ ] **Step 2: Run test and confirm failure**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=ForeignKeyTriggerManagerTest' test
```

Expected: compile failure because `ForeignKeyTriggerManager` does not exist.

- [ ] **Step 3: Implement FK trigger manager**

Create `ForeignKeyTriggerManager`:

```java
package com.bank.migration.dataonly;

import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ForeignKeyTriggerManager {
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;

    public ForeignKeyTriggerManager(
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
    }

    public void disableAll(String targetSchema, List<TableMetadata> tables) {
        for (TableMetadata table : included(tables)) {
            targetJdbc.execute("alter table " + targetRenderer.renderQualifiedName(targetSchema, table.name()) + " disable trigger all");
        }
    }

    public void enableAll(String targetSchema, List<TableMetadata> tables) {
        for (TableMetadata table : included(tables)) {
            targetJdbc.execute("alter table " + targetRenderer.renderQualifiedName(targetSchema, table.name()) + " enable trigger all");
        }
    }

    private static List<TableMetadata> included(List<TableMetadata> tables) {
        return tables.stream()
            .filter(table -> table.status() != ObjectStatus.EXCLUDED)
            .toList();
    }
}
```

- [ ] **Step 4: Verify Task 4**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=ForeignKeyTriggerManagerTest' test
```

Expected: `BUILD SUCCESS`.

## Task 5: Add Table Load Ordering

**Files:**
- Create: `src/main/java/com/bank/migration/dataonly/TableLoadOrderPlanner.java`
- Test: `src/test/java/com/bank/migration/dataonly/TableLoadOrderPlannerTest.java`

- [ ] **Step 1: Write load-order tests**

Create `TableLoadOrderPlannerTest`:

```java
class TableLoadOrderPlannerTest {
    private final TableLoadOrderPlanner planner = new TableLoadOrderPlanner();

    @Test
    void ordersParentBeforeChildForForeignKeys() {
        TableMetadata parent = table("CUSTOMER", List.of(pk("PK_CUSTOMER", "CUSTOMER_ID")));
        TableMetadata child = table("ACCOUNT", List.of(
            pk("PK_ACCOUNT", "ACCOUNT_ID"),
            fk("FK_ACCOUNT_CUSTOMER", "CUSTOMER_ID", "CUSTOMER", "CUSTOMER_ID")
        ));
        MigrationManifest manifest = new MigrationManifest("run-1", "BANK_CORE", List.of(child, parent), List.of());

        List<TableMetadata> ordered = planner.order(manifest, DataOnlyForeignKeyHandling.ORDER_ONLY);

        assertThat(ordered).extracting(TableMetadata::name).containsExactly("CUSTOMER", "ACCOUNT");
    }

    @Test
    void ignoresSelfReferencingForeignKey() {
        TableMetadata employee = table("EMPLOYEE", List.of(
            pk("PK_EMPLOYEE", "EMP_ID"),
            fk("FK_EMPLOYEE_MANAGER", "MANAGER_ID", "EMPLOYEE", "EMP_ID")
        ));
        MigrationManifest manifest = new MigrationManifest("run-1", "BANK_CORE", List.of(employee), List.of());

        List<TableMetadata> ordered = planner.order(manifest, DataOnlyForeignKeyHandling.ORDER_ONLY);

        assertThat(ordered).extracting(TableMetadata::name).containsExactly("EMPLOYEE");
    }

    @Test
    void orderOnlyBlocksCircularForeignKeys() {
        TableMetadata a = table("A", List.of(pk("PK_A", "ID"), fk("FK_A_B", "B_ID", "B", "ID")));
        TableMetadata b = table("B", List.of(pk("PK_B", "ID"), fk("FK_B_A", "A_ID", "A", "ID")));
        MigrationManifest manifest = new MigrationManifest("run-1", "BANK_CORE", List.of(a, b), List.of());

        assertThatThrownBy(() -> planner.order(manifest, DataOnlyForeignKeyHandling.ORDER_ONLY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Circular foreign key dependency");
    }

    @Test
    void disableReenableAllowsCircularForeignKeys() {
        TableMetadata a = table("A", List.of(pk("PK_A", "ID"), fk("FK_A_B", "B_ID", "B", "ID")));
        TableMetadata b = table("B", List.of(pk("PK_B", "ID"), fk("FK_B_A", "A_ID", "A", "ID")));
        MigrationManifest manifest = new MigrationManifest("run-1", "BANK_CORE", List.of(a, b), List.of());

        List<TableMetadata> ordered = planner.order(manifest, DataOnlyForeignKeyHandling.DISABLE_REENABLE);

        assertThat(ordered).extracting(TableMetadata::name).containsExactly("A", "B");
    }

    private static TableMetadata table(String name, List<KeyMetadata> keys) {
        return new TableMetadata("BANK_CORE", name, ObjectStatus.READY, List.of(), keys, List.of());
    }

    private static KeyMetadata pk(String name, String column) {
        return new KeyMetadata(name, "PRIMARY_KEY", List.of(column), null, null);
    }

    private static KeyMetadata fk(String name, String column, String referencedTable, String referencedColumn) {
        return new KeyMetadata(name, "FOREIGN_KEY", List.of(column), referencedTable, List.of(referencedColumn));
    }
}
```

- [ ] **Step 2: Run test and confirm failure**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=TableLoadOrderPlannerTest' test
```

Expected: compile failure because `TableLoadOrderPlanner` does not exist.

- [ ] **Step 3: Implement load order planner**

Create `TableLoadOrderPlanner`:

```java
package com.bank.migration.dataonly;

import com.bank.migration.config.DataOnlyForeignKeyHandling;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TableLoadOrderPlanner {
    public List<TableMetadata> order(MigrationManifest manifest, DataOnlyForeignKeyHandling handling) {
        List<TableMetadata> included = manifest.tables().stream()
            .filter(table -> table.status() != ObjectStatus.EXCLUDED)
            .toList();
        if (handling != DataOnlyForeignKeyHandling.ORDER_ONLY) {
            return included;
        }
        return topologicalOrder(included);
    }

    private List<TableMetadata> topologicalOrder(List<TableMetadata> tables) {
        Map<String, TableMetadata> byName = new LinkedHashMap<>();
        for (TableMetadata table : tables) {
            byName.put(key(table.name()), table);
        }

        Map<String, Set<String>> childrenByParent = new HashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (TableMetadata table : tables) {
            inDegree.put(key(table.name()), 0);
        }

        for (TableMetadata child : tables) {
            String childName = key(child.name());
            for (KeyMetadata fk : child.keys()) {
                if (!"FOREIGN_KEY".equalsIgnoreCase(fk.type()) || fk.referencedTableName() == null) {
                    continue;
                }
                String parentName = key(fk.referencedTableName());
                if (parentName.equals(childName) || !byName.containsKey(parentName)) {
                    continue;
                }
                childrenByParent.computeIfAbsent(parentName, ignored -> new LinkedHashSet<>()).add(childName);
                inDegree.put(childName, inDegree.get(childName) + 1);
            }
        }

        ArrayDeque<String> ready = new ArrayDeque<>();
        inDegree.forEach((table, degree) -> {
            if (degree == 0) {
                ready.add(table);
            }
        });

        List<TableMetadata> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String parent = ready.removeFirst();
            ordered.add(byName.get(parent));
            for (String child : childrenByParent.getOrDefault(parent, Set.of())) {
                int newDegree = inDegree.get(child) - 1;
                inDegree.put(child, newDegree);
                if (newDegree == 0) {
                    ready.add(child);
                }
            }
        }

        if (ordered.size() != tables.size()) {
            List<String> cycleTables = inDegree.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .toList();
            throw new IllegalStateException("Circular foreign key dependency among tables: " + String.join(", ", cycleTables));
        }
        return List.copyOf(ordered);
    }

    private static String key(String name) {
        return name.toUpperCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: Verify Task 5**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=TableLoadOrderPlannerTest' test
```

Expected: `BUILD SUCCESS`.

## Task 6: Validate Actual Target FKs After Data-Only Load

**Files:**
- Create: `src/main/java/com/bank/migration/dataonly/TargetForeignKeyMetadata.java`
- Create: `src/main/java/com/bank/migration/dataonly/TargetForeignKeyScanner.java`
- Create: `src/main/java/com/bank/migration/dataonly/TargetForeignKeyValidator.java`
- Test: `src/test/java/com/bank/migration/dataonly/TargetForeignKeyValidatorTest.java`

- [ ] **Step 1: Write target FK validation tests**

Create `TargetForeignKeyValidatorTest`:

```java
@ExtendWith(MockitoExtension.class)
class TargetForeignKeyValidatorTest {
    @Mock JdbcTemplate targetJdbc;
    @Mock IdentifierRenderer targetRenderer;
    @Mock TargetForeignKeyScanner scanner;

    @BeforeEach
    void setUp() {
        when(targetRenderer.renderQualifiedName(anyString(), anyString())).thenAnswer(inv ->
            inv.getArgument(0, String.class).toLowerCase(Locale.ROOT) + "." + inv.getArgument(1, String.class).toLowerCase(Locale.ROOT)
        );
        when(targetRenderer.render(anyString())).thenAnswer(inv -> inv.getArgument(0, String.class).toLowerCase(Locale.ROOT));
    }

    @Test
    void passesWhenNoTargetForeignKeyOrphansExist() {
        TargetForeignKeyMetadata fk = new TargetForeignKeyMetadata(
            "fk_account_customer",
            "ACCOUNT",
            List.of("CUSTOMER_ID"),
            "CUSTOMER",
            List.of("ID")
        );
        when(scanner.scan("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"))).thenReturn(List.of(fk));
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        TargetForeignKeyValidator validator = new TargetForeignKeyValidator(targetJdbc, targetRenderer, scanner);

        List<ValidationResult> results = validator.validate("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.name()).isEqualTo("target-foreign-key-orphans");
            assertThat(result.status()).isEqualTo(ValidationStatus.PASS);
            assertThat(result.objectName()).isEqualTo("ACCOUNT.fk_account_customer");
            assertThat(result.message()).isEqualTo("orphans=0");
        });
    }

    @Test
    void failsWhenTargetForeignKeyOrphansExist() {
        TargetForeignKeyMetadata fk = new TargetForeignKeyMetadata(
            "fk_account_customer",
            "ACCOUNT",
            List.of("CUSTOMER_ID"),
            "CUSTOMER",
            List.of("ID")
        );
        when(scanner.scan("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"))).thenReturn(List.of(fk));
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);

        TargetForeignKeyValidator validator = new TargetForeignKeyValidator(targetJdbc, targetRenderer, scanner);

        List<ValidationResult> results = validator.validate("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo(ValidationStatus.FAIL);
            assertThat(result.message()).isEqualTo("orphans=3");
        });
    }
}
```

- [ ] **Step 2: Run test and confirm failure**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=TargetForeignKeyValidatorTest' test
```

Expected: compile failure because target FK classes do not exist.

- [ ] **Step 3: Implement metadata record**

Create `TargetForeignKeyMetadata`:

```java
package com.bank.migration.dataonly;

import java.util.List;

public record TargetForeignKeyMetadata(
    String constraintName,
    String childTable,
    List<String> childColumns,
    String parentTable,
    List<String> parentColumns
) {
    public TargetForeignKeyMetadata {
        childColumns = List.copyOf(childColumns);
        parentColumns = List.copyOf(parentColumns);
    }
}
```

- [ ] **Step 4: Implement target FK scanner**

Create `TargetForeignKeyScanner`:

```java
package com.bank.migration.dataonly;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TargetForeignKeyScanner {
    private final JdbcTemplate targetJdbc;

    public TargetForeignKeyScanner(@Qualifier("targetJdbc") JdbcTemplate targetJdbc) {
        this.targetJdbc = targetJdbc;
    }

    public List<TargetForeignKeyMetadata> scan(String targetSchema, Set<String> includedTables) {
        String sql = """
            select
              con.conname,
              child.relname as child_table,
              parent.relname as parent_table,
              child_att.attname as child_column,
              parent_att.attname as parent_column,
              ord.n as column_position
            from pg_catalog.pg_constraint con
            join pg_catalog.pg_class child on child.oid = con.conrelid
            join pg_catalog.pg_namespace child_ns on child_ns.oid = child.relnamespace
            join pg_catalog.pg_class parent on parent.oid = con.confrelid
            join unnest(con.conkey, con.confkey) with ordinality as ord(child_attnum, parent_attnum, n) on true
            join pg_catalog.pg_attribute child_att on child_att.attrelid = child.oid and child_att.attnum = ord.child_attnum
            join pg_catalog.pg_attribute parent_att on parent_att.attrelid = parent.oid and parent_att.attnum = ord.parent_attnum
            where con.contype = 'f'
              and child_ns.nspname = ?
            order by con.conname, ord.n
            """;

        List<FkColumnRow> rows = targetJdbc.query(sql, (rs, rowNum) -> new FkColumnRow(
            rs.getString("conname"),
            rs.getString("child_table"),
            rs.getString("parent_table"),
            rs.getString("child_column"),
            rs.getString("parent_column")
        ), targetSchema);

        List<TargetForeignKeyMetadata> result = new ArrayList<>();
        String current = null;
        String childTable = null;
        String parentTable = null;
        List<String> childColumns = new ArrayList<>();
        List<String> parentColumns = new ArrayList<>();

        for (FkColumnRow row : rows) {
            if (!includedTables.contains(row.childTable().toUpperCase()) || !includedTables.contains(row.parentTable().toUpperCase())) {
                continue;
            }
            if (current != null && !current.equals(row.constraintName())) {
                result.add(new TargetForeignKeyMetadata(current, childTable, childColumns, parentTable, parentColumns));
                childColumns = new ArrayList<>();
                parentColumns = new ArrayList<>();
            }
            current = row.constraintName();
            childTable = row.childTable();
            parentTable = row.parentTable();
            childColumns.add(row.childColumn());
            parentColumns.add(row.parentColumn());
        }
        if (current != null) {
            result.add(new TargetForeignKeyMetadata(current, childTable, childColumns, parentTable, parentColumns));
        }
        return List.copyOf(result);
    }

    private record FkColumnRow(
        String constraintName,
        String childTable,
        String parentTable,
        String childColumn,
        String parentColumn
    ) {}
}
```

- [ ] **Step 5: Implement target FK validator**

Create `TargetForeignKeyValidator`:

```java
package com.bank.migration.dataonly;

import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.validate.ValidationResult;
import com.bank.migration.validate.ValidationStatus;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TargetForeignKeyValidator {
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;
    private final TargetForeignKeyScanner scanner;

    public TargetForeignKeyValidator(
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer,
        TargetForeignKeyScanner scanner
    ) {
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
        this.scanner = scanner;
    }

    public List<ValidationResult> validate(String targetSchema, Set<String> includedTables) {
        return scanner.scan(targetSchema, includedTables).stream()
            .map(fk -> validate(targetSchema, fk))
            .toList();
    }

    private ValidationResult validate(String targetSchema, TargetForeignKeyMetadata fk) {
        String join = IntStream.range(0, fk.childColumns().size())
            .mapToObj(i -> "c." + targetRenderer.render(fk.childColumns().get(i)) + " = p." + targetRenderer.render(fk.parentColumns().get(i)))
            .reduce((left, right) -> left + " and " + right)
            .orElseThrow();
        String notNull = fk.childColumns().stream()
            .map(column -> "c." + targetRenderer.render(column) + " is not null")
            .reduce((left, right) -> left + " and " + right)
            .orElse("true");
        String parentMissing = fk.parentColumns().stream()
            .map(column -> "p." + targetRenderer.render(column) + " is null")
            .findFirst()
            .orElse("false");

        String sql = "select count(*) from "
            + targetRenderer.renderQualifiedName(targetSchema, fk.childTable()) + " c left join "
            + targetRenderer.renderQualifiedName(targetSchema, fk.parentTable()) + " p on " + join
            + " where " + notNull + " and " + parentMissing;

        Long count = targetJdbc.queryForObject(sql, Long.class);
        long orphans = count == null ? 0L : count;
        return new ValidationResult(
            "target-foreign-key-orphans",
            orphans == 0L ? ValidationStatus.PASS : ValidationStatus.FAIL,
            fk.childTable().toUpperCase() + "." + fk.constraintName(),
            "orphans=" + orphans
        );
    }
}
```

- [ ] **Step 6: Verify Task 6**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=TargetForeignKeyValidatorTest' test
```

Expected: `BUILD SUCCESS`.

## Task 7: Wire Data-Only Mode into Orchestrator

**Files:**
- Modify: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`
- Test: `src/test/java/com/bank/migration/orchestrator/MigrationOrchestratorPipelineTest.java`

- [ ] **Step 1: Add orchestrator mocks**

Add mocks to `MigrationOrchestratorPipelineTest`:

```java
@Mock DataOnlyTargetReadinessService dataOnlyTargetReadinessService;
@Mock ForeignKeyTriggerManager foreignKeyTriggerManager;
@Mock TableLoadOrderPlanner tableLoadOrderPlanner;
@Mock TargetForeignKeyValidator targetForeignKeyValidator;
```

Use the autowired constructor in tests that need data-only wiring.

- [ ] **Step 2: Write data-only happy-path test**

Add:

```java
@Test
void dataOnlyModeSkipsDdlViewsAndLoadsDataWithFkDisableReenable() throws Exception {
    MigrationProperties props = dataOnlyProperties(DataOnlyForeignKeyHandling.DISABLE_REENABLE);
    TableMetadata parent = table("CUSTOMER");
    TableMetadata child = table("ACCOUNT");
    MigrationManifest manifest = new MigrationManifest("run-001", "BANK_CORE", List.of(parent, child), List.of(
        new ViewMetadata("BANK_CORE", "VW_ACCOUNT", ObjectStatus.READY, "select * from ACCOUNT", List.of(), List.of())
    ));
    ChunkPlan parentChunk = new ChunkPlan("CUSTOMER-000001", "ID", "ID >= 1 and ID <= 10", "PRIMARY_KEY_RANGE");
    ChunkPlan childChunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 10", "PRIMARY_KEY_RANGE");
    List<ValidationResult> baseValidations = List.of(new ValidationResult("row-count", ValidationStatus.PASS, "ACCOUNT", "source=10 target=10"));
    List<ValidationResult> targetFkValidations = List.of(new ValidationResult("target-foreign-key-orphans", ValidationStatus.PASS, "ACCOUNT.fk_account_customer", "orphans=0"));

    when(preflightService.run("bank_core", true, MigrationMode.DATA_ONLY)).thenReturn(List.of(
        new PreflightCheck("source-connectivity", true, "ok"),
        new PreflightCheck("target-connectivity", true, "ok")
    ));
    when(scanner.scan(anyString(), eq("BANK_CORE"))).thenReturn(manifest);
    when(readinessEvaluator.evaluate(any(MigrationManifest.class), eq(props))).thenReturn(new ReadinessReport(List.of(), false));
    when(dataOnlyTargetReadinessService.check(any(MigrationManifest.class), eq("bank_core"), eq(TargetDataPolicy.REQUIRE_EMPTY)))
        .thenReturn(List.of(new PreflightCheck("data-only-target-table-CUSTOMER", true, "ok")));
    when(tableLoadOrderPlanner.order(any(MigrationManifest.class), eq(DataOnlyForeignKeyHandling.DISABLE_REENABLE)))
        .thenReturn(List.of(parent, child));
    when(chunkBoundsService.bounds(parent)).thenReturn(new ChunkBounds(1L, 10L));
    when(chunkBoundsService.bounds(child)).thenReturn(new ChunkBounds(1L, 10L));
    when(chunkPlanner.plan(parent, 1L, 10L, 5000)).thenReturn(List.of(parentChunk));
    when(chunkPlanner.plan(child, 1L, 10L, 5000)).thenReturn(List.of(childChunk));
    when(dataCopyService.copyChunk(parent, "bank_core", parentChunk)).thenReturn(new TableCopyResult(10, 10));
    when(dataCopyService.copyChunk(child, "bank_core", childChunk)).thenReturn(new TableCopyResult(10, 10));
    when(validationCoordinator.validate(any(MigrationManifest.class), eq("bank_core"))).thenReturn(baseValidations);
    when(targetForeignKeyValidator.validate(eq("bank_core"), eq(Set.of("CUSTOMER", "ACCOUNT")))).thenReturn(targetFkValidations);

    MigrationOrchestrator orchestrator = dataOnlyOrchestrator();

    orchestrator.run(props);

    verify(targetSchemaService, never()).prepareCleanSchema(anyString());
    verify(ddlPlanner, never()).plan(anyString(), any(TableMetadata.class));
    verify(ddlApplier, never()).apply(anyList());
    verify(viewPlanner, never()).plan(anyString(), any(ViewMetadata.class));
    verify(viewApplier, never()).applyReadyViews(anyString(), anyList());
    verify(foreignKeyTriggerManager).disableAll("bank_core", List.of(parent, child));
    verify(foreignKeyTriggerManager).enableAll("bank_core", List.of(parent, child));
    verify(dataCopyService).copyChunk(parent, "bank_core", parentChunk);
    verify(dataCopyService).copyChunk(child, "bank_core", childChunk);
    verify(targetForeignKeyValidator).validate("bank_core", Set.of("CUSTOMER", "ACCOUNT"));
}
```

Add helper methods:

```java
private MigrationProperties dataOnlyProperties(DataOnlyForeignKeyHandling fkHandling) {
    return new MigrationProperties(
        new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
        new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
        true,
        new MigrationProperties.Batch(5000, 5000, 2),
        new MigrationProperties.Reports("build/reports"),
        null,
        null,
        null,
        UnsupportedTypePolicy.FAIL,
        MigrationMode.DATA_ONLY,
        new MigrationProperties.DataOnly(TargetDataPolicy.REQUIRE_EMPTY, fkHandling)
    );
}

private static TableMetadata table(String name) {
    return new TableMetadata(
        "BANK_CORE",
        name,
        ObjectStatus.READY,
        List.of(new ColumnMetadata("ID", "NUMBER", 18, 0, false, null)),
        List.of(),
        List.of()
    );
}
```

Adjust constructor arguments if the final `MigrationProperties` constructor order differs after Task 1.

- [ ] **Step 3: Write re-enable-on-load-failure test**

Add:

```java
@Test
void dataOnlyModeReenablesTriggersWhenDataLoadFails() throws Exception {
    MigrationProperties props = dataOnlyProperties(DataOnlyForeignKeyHandling.DISABLE_REENABLE);
    TableMetadata account = table("ACCOUNT");
    MigrationManifest manifest = new MigrationManifest("run-001", "BANK_CORE", List.of(account), List.of());
    ChunkPlan chunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 10", "PRIMARY_KEY_RANGE");

    when(preflightService.run("bank_core", true, MigrationMode.DATA_ONLY)).thenReturn(List.of(
        new PreflightCheck("source-connectivity", true, "ok"),
        new PreflightCheck("target-connectivity", true, "ok")
    ));
    when(scanner.scan(anyString(), eq("BANK_CORE"))).thenReturn(manifest);
    when(readinessEvaluator.evaluate(any(MigrationManifest.class), eq(props))).thenReturn(new ReadinessReport(List.of(), false));
    when(dataOnlyTargetReadinessService.check(any(MigrationManifest.class), eq("bank_core"), eq(TargetDataPolicy.REQUIRE_EMPTY)))
        .thenReturn(List.of(new PreflightCheck("data-only-target-table-ACCOUNT", true, "ok")));
    when(tableLoadOrderPlanner.order(any(MigrationManifest.class), eq(DataOnlyForeignKeyHandling.DISABLE_REENABLE))).thenReturn(List.of(account));
    when(chunkBoundsService.bounds(account)).thenReturn(new ChunkBounds(1L, 10L));
    when(chunkPlanner.plan(account, 1L, 10L, 5000)).thenReturn(List.of(chunk));
    when(dataCopyService.copyChunk(account, "bank_core", chunk)).thenThrow(new DataAccessResourceFailureException("copy failed"));

    MigrationOrchestrator orchestrator = dataOnlyOrchestrator();

    assertThatThrownBy(() -> orchestrator.run(props))
        .isInstanceOf(ChunkMigrationException.class)
        .hasMessageContaining("copy failed");

    verify(foreignKeyTriggerManager).disableAll("bank_core", List.of(account));
    verify(foreignKeyTriggerManager).enableAll("bank_core", List.of(account));
}
```

- [ ] **Step 4: Run test and confirm failure**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=MigrationOrchestratorPipelineTest' test
```

Expected: compile failure because orchestrator does not accept data-only dependencies and does not branch on mode.

- [ ] **Step 5: Add orchestrator dependencies**

Add final fields:

```java
private final DataOnlyTargetReadinessService dataOnlyTargetReadinessService;
private final ForeignKeyTriggerManager foreignKeyTriggerManager;
private final TableLoadOrderPlanner tableLoadOrderPlanner;
private final TargetForeignKeyValidator targetForeignKeyValidator;
```

Add them to the autowired constructor. Existing test constructors can pass `null` through convenience constructors to preserve older tests.

- [ ] **Step 6: Extract data-load helper**

Move the current data-load loop into:

```java
private long loadTables(
    String runId,
    String targetSchema,
    List<TableMetadata> tables,
    MigrationProperties properties,
    List<ChunkStrategyRecord> chunkStrategies
) {
    long totalTablesLoaded = 0;
    for (TableMetadata table : tables) {
        if (table.status() == ObjectStatus.EXCLUDED) {
            continue;
        }
        ChunkBounds bounds = chunkBoundsService.bounds(table);
        List<ChunkPlan> chunks = chunkPlanner.plan(table, bounds.minInclusive(), bounds.maxInclusive(), properties.batch().chunkSize());
        recordChunkStrategies(table, chunks, chunkStrategies);
        new TableMigrationTasklet(runId, targetSchema, table, chunks, dataCopyService, checkpointStore, errorLogStore).run();
        totalTablesLoaded++;
    }
    return totalTablesLoaded;
}
```

Add:

```java
private void recordChunkStrategies(TableMetadata table, List<ChunkPlan> chunks, List<ChunkStrategyRecord> chunkStrategies) {
    String strategyName = ChunkStrategySelector.select(table).name();
    if (chunks.isEmpty()) {
        chunkStrategies.add(new ChunkStrategyRecord(table.name(), null, strategyName, null, null));
        return;
    }
    for (ChunkPlan chunk : chunks) {
        chunkStrategies.add(new ChunkStrategyRecord(
            table.name(),
            chunk.chunkId(),
            chunk.strategy(),
            chunk.columnName(),
            chunk.whereClause()
        ));
    }
}
```

- [ ] **Step 7: Add data-only branch after readiness evaluation**

In `run`, after readiness and before `ddl-application`, add:

```java
if (properties.mode() == MigrationMode.DATA_ONLY) {
    runDataOnlyPipeline(runId, targetSchema, manifest, properties, validations, chunkStrategies);
    List<ExcludedObjectDecision> excludedDecisions = extractExcludedDecisions(manifest);
    String runStatus = status(validations, List.of());
    if (runStatusStore != null) {
        runStatusStore.finishRun(runId, Instant.now(), runStatus);
    }
    reportWriter.write(new MigrationReport(
        runId,
        runStatus,
        properties.mode().name(),
        List.of("ddl-application", "constraints-and-indexes", "view-application"),
        started,
        Instant.now(),
        validations,
        errors,
        readinessFindings,
        excludedDecisions,
        chunkStrategies
    ), Path.of(properties.reports().outputDir()));
    return;
}
```

The `MigrationReport` constructor in this snippet is added in Task 8.

- [ ] **Step 8: Implement `runDataOnlyPipeline`**

Add:

```java
private void runDataOnlyPipeline(
    String runId,
    String targetSchema,
    MigrationManifest manifest,
    MigrationProperties properties,
    List<ValidationResult> validations,
    List<ChunkStrategyRecord> chunkStrategies
) {
    List<PreflightCheck> dataOnlyChecks = dataOnlyTargetReadinessService.check(
        manifest,
        targetSchema,
        properties.dataOnly().targetDataPolicy()
    );
    ensurePreflightPassed(dataOnlyChecks);

    List<TableMetadata> orderedTables = tableLoadOrderPlanner.order(
        manifest,
        properties.dataOnly().foreignKeyHandling()
    );

    boolean triggersDisabled = false;
    Exception loadFailure = null;
    try {
        if (properties.dataOnly().foreignKeyHandling() == DataOnlyForeignKeyHandling.DISABLE_REENABLE) {
            foreignKeyTriggerManager.disableAll(targetSchema, orderedTables);
            triggersDisabled = true;
        }
        loadTables(runId, targetSchema, orderedTables, properties, chunkStrategies);
    } catch (Exception ex) {
        loadFailure = ex;
        throw ex;
    } finally {
        if (triggersDisabled) {
            try {
                foreignKeyTriggerManager.enableAll(targetSchema, orderedTables);
            } catch (Exception enableFailure) {
                if (loadFailure != null) {
                    loadFailure.addSuppressed(enableFailure);
                } else {
                    throw enableFailure;
                }
            }
        }
    }

    validations.addAll(validationCoordinator.validate(manifest, targetSchema));
    Set<String> includedTableNames = orderedTables.stream()
        .filter(table -> table.status() != ObjectStatus.EXCLUDED)
        .map(table -> table.name().toUpperCase(Locale.ROOT))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    validations.addAll(targetForeignKeyValidator.validate(targetSchema, includedTableNames));
}
```

Add imports:

```java
import com.bank.migration.config.DataOnlyForeignKeyHandling;
import com.bank.migration.config.MigrationMode;
import com.bank.migration.dataonly.DataOnlyTargetReadinessService;
import com.bank.migration.dataonly.ForeignKeyTriggerManager;
import com.bank.migration.dataonly.TableLoadOrderPlanner;
import com.bank.migration.dataonly.TargetForeignKeyValidator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
```

- [ ] **Step 9: Verify Task 7**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=MigrationOrchestratorPipelineTest,MigrationOrchestratorTest' test
```

Expected: `BUILD SUCCESS`.

## Task 8: Add Mode and Skipped Phases to Reports

**Files:**
- Modify: `src/main/java/com/bank/migration/report/MigrationReport.java`
- Modify: `src/main/java/com/bank/migration/report/ReportWriter.java`
- Test: `src/test/java/com/bank/migration/report/ReportWriterTest.java`

- [ ] **Step 1: Write report test**

Add to `ReportWriterTest`:

```java
@Test
void writesMigrationModeAndSkippedPhases() throws Exception {
    Path outputDir = tempDir.resolve("reports");
    MigrationReport report = new MigrationReport(
        "run-123",
        "PASS",
        "DATA_ONLY",
        List.of("ddl-application", "constraints-and-indexes", "view-application"),
        Instant.parse("2026-06-04T00:00:00Z"),
        Instant.parse("2026-06-04T00:01:00Z"),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of()
    );

    new ReportWriter().write(report, outputDir);

    String json = Files.readString(outputDir.resolve("run-123-summary.json"));
    String html = Files.readString(outputDir.resolve("run-123-summary.html"));
    assertThat(json).contains("\"mode\":\"DATA_ONLY\"");
    assertThat(json).contains("ddl-application");
    assertThat(html).contains("DATA_ONLY");
    assertThat(html).contains("Skipped Phases");
}
```

- [ ] **Step 2: Run test and confirm failure**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=ReportWriterTest' test
```

Expected: compile failure because `MigrationReport` has no mode/skipped-phases constructor.

- [ ] **Step 3: Extend report record**

Modify `MigrationReport` to include:

```java
String mode,
List<String> skippedPhases,
```

Keep existing constructors by delegating with:

```java
this(runId, status, "FULL", List.of(), startedAt, completedAt, validations, errors, readinessFindings, excludedObjectDecisions, chunkStrategies);
```

In the compact constructor:

```java
mode = mode == null || mode.isBlank() ? "FULL" : mode;
skippedPhases = List.copyOf(skippedPhases == null ? List.of() : skippedPhases);
```

- [ ] **Step 4: Render mode and skipped phases in HTML**

In `ReportWriter.html`, add:

```java
builder.append("<p>Mode: ").append(escape(report.mode())).append("</p>");
if (!report.skippedPhases().isEmpty()) {
    builder.append("<h2>Skipped Phases</h2><ul>");
    report.skippedPhases().forEach(phase -> builder.append("<li>").append(escape(phase)).append("</li>"));
    builder.append("</ul>");
}
```

Add a private `escape` helper if one does not exist:

```java
private static String escape(String value) {
    if (value == null) {
        return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
}
```

- [ ] **Step 5: Verify Task 8**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=ReportWriterTest,MigrationOrchestratorPipelineTest' test
```

Expected: `BUILD SUCCESS`.

## Task 9: Documentation and Operational Guardrails

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add data-only configuration section**

Add:

````markdown
## Data-Only Mode

Use data-only mode when GaussDB/openGauss DDL has already been created by DBA-approved scripts and the migration tool must only copy Oracle table data.

```yaml
migration:
  mode: DATA_ONLY
  clean-load: true
  data-only:
    target-data-policy: REQUIRE_EMPTY
    foreign-key-handling: DISABLE_REENABLE
````

In `DATA_ONLY` mode the tool skips:

- target schema cleanup
- table creation
- primary key, unique, index, and foreign key DDL
- view creation

The tool still runs:

- source metadata scan
- exclusions
- readiness checks for data-copy risks
- target table/column compatibility checks
- chunked data copy
- row-count validation
- duplicate-key validation
- source-manifest FK validation
- target-catalog FK validation
- audit and filesystem reports
```

- [ ] **Step 2: Add FK handling notes**

Add:

````markdown
### Foreign Key Handling in Data-Only Mode

`DISABLE_REENABLE` executes `ALTER TABLE <schema>.<table> DISABLE TRIGGER ALL` before loading included tables and `ALTER TABLE <schema>.<table> ENABLE TRIGGER ALL` after loading. This usually requires table owner, DBA, or elevated privileges. The tool always attempts to re-enable triggers in a `finally` block.

If the migration user cannot disable triggers, use:

```yaml
migration:
  data-only:
    foreign-key-handling: ORDER_ONLY
````

`ORDER_ONLY` loads parent tables before child tables based on Oracle FK metadata and fails on circular FK dependencies. Use `DBA_MANAGED` only when the DBA disables and re-enables FK triggers outside the tool. All modes run target FK orphan validation after data load.
```

- [ ] **Step 3: Verify docs compile with tests**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' test
```

Expected: `BUILD SUCCESS`.

## Task 10: Full Verification

**Files:**
- All files touched above.

- [ ] **Step 1: Run focused suite**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' '-Dtest=MigrationPropertiesTest,PreflightServiceTest,DataOnlyTargetReadinessServiceTest,ForeignKeyTriggerManagerTest,TableLoadOrderPlannerTest,TargetForeignKeyValidatorTest,MigrationOrchestratorPipelineTest,ReportWriterTest' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run full suite**

Run:

```powershell
& 'G:\workspace\db-migration\.maven\apache-maven-3.9.9\bin\mvn.cmd' test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Run whitespace check**

Run:

```powershell
git diff --check
```

Expected: no new whitespace errors from files touched by this plan. If older branch-wide whitespace errors remain, list them separately and do not hide them in the final report.

- [ ] **Step 4: Produce worker summary**

Final worker summary must include:

```text
Completed:
- DATA_ONLY config and defaults
- data-only target compatibility preflight
- FK trigger disable/reenable manager
- parent-before-child load ordering
- target-catalog FK orphan validation
- orchestrator branch that skips DDL/view phases
- report and README updates

Tests:
- focused suite command and result
- full suite command and result
- git diff --check result

Known issues:
- list only real remaining blockers
```

## Acceptance Gate

- `migration.mode=FULL` keeps current behavior.
- `migration.mode=DATA_ONLY` never calls `TargetSchemaService.prepareCleanSchema`, `TableDdlPlanner.plan`, `DdlApplier.apply`, `ViewPlanner.plan`, or `ViewApplier.applyReadyViews`.
- Data-only mode fails before data copy if an included target table is missing.
- Data-only mode fails before data copy if a required target column is missing.
- Data-only mode fails before data copy if an included target table has existing rows under `REQUIRE_EMPTY`.
- Data-only mode fails before data copy if the target has extra `NOT NULL` columns without default, identity, or generated expression.
- `DISABLE_REENABLE` always attempts to re-enable triggers after load failure.
- `ORDER_ONLY` loads parent tables before child tables and blocks circular FK dependencies.
- Target-catalog FK orphan validation runs after data load in every data-only FK handling mode.
- Final report includes `mode=DATA_ONLY` and skipped phases.
- Full Maven test suite passes.

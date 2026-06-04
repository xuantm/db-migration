package com.bank.migration.dataonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.bank.migration.config.TargetDataPolicy;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.preflight.PreflightCheck;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class DataOnlyTargetReadinessServiceTest {
    @Mock JdbcTemplate targetJdbc;
    @Mock IdentifierRenderer targetRenderer;

    @BeforeEach
    void setUp() {
        lenient().when(targetRenderer.physicalName(anyString())).thenAnswer(inv -> inv.getArgument(0, String.class).toLowerCase(Locale.ROOT));
        lenient().when(targetRenderer.renderQualifiedName(anyString(), anyString())).thenAnswer(inv ->
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

    @Test
    void failsWhenTargetIsNonBaseTable() {
        TableMetadata table = table("ACCOUNT", List.of("ID"));
        // When queried with table_type = 'BASE TABLE', a view/non-base table will return 0 rows.
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

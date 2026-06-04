package com.bank.migration.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.SchemaObjectType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class OracleMetadataScannerTest {
    @Mock JdbcTemplate jdbc;
    @Mock ResultSet resultSet;

    @Test
    @SuppressWarnings("unchecked")
    void scansTablesColumnsKeysIndexesAndViewsWithUppercaseOracleOwner() {
        when(jdbc.query(eq(OracleMetadataScanner.TABLE_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of("ACCOUNT"));
        when(jdbc.query(eq(OracleMetadataScanner.COLUMN_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.ColumnRow("ACCOUNT", "ID", "NUMBER", 19, 0, "N", null)));
        when(jdbc.query(eq(OracleMetadataScanner.KEY_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.KeyRow("ACCOUNT", "PK_ACCOUNT", "PRIMARY_KEY", "ID", null, null)));
        when(jdbc.query(eq(OracleMetadataScanner.INDEX_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.IndexRow("ACCOUNT", "IX_ACCOUNT_ID", "NONUNIQUE", "ID", null)));
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.ViewRow("VW_ACCOUNT", "select ID from ACCOUNT")));
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_DEPENDENCY_SQL), any(RowMapper.class), eq("BANK_CORE"), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.ViewDependencyRow("VW_ACCOUNT", "ACCOUNT")));
        
        when(jdbc.query(contains("object_type in"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of());
        when(jdbc.query(contains("constraint_type = 'C'"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of());

        OracleMetadataScanner scanner = new OracleMetadataScanner(jdbc);

        MigrationManifest manifest = scanner.scan("run-001", "bank_core");

        assertThat(manifest.tables()).hasSize(1);
        assertThat(manifest.tables().getFirst().name()).isEqualTo("ACCOUNT");
        assertThat(manifest.tables().getFirst().status()).isEqualTo(ObjectStatus.READY);
        assertThat(manifest.tables().getFirst().columns()).hasSize(1);
        assertThat(manifest.tables().getFirst().schema()).isEqualTo("BANK_CORE");
        assertThat(manifest.views()).hasSize(1);
        assertThat(manifest.views().getFirst().name()).isEqualTo("VW_ACCOUNT");
        assertThat(manifest.views().getFirst().dependencies()).containsExactly("ACCOUNT");
        assertThat(manifest.sourceSchema()).isEqualTo("BANK_CORE");
        assertThat(OracleMetadataScanner.INDEX_SQL).contains("not exists");
        assertThat(OracleMetadataScanner.INDEX_SQL).contains("constraint_type in ('P', 'U')");
    }

    @Test
    @SuppressWarnings("unchecked")
    void groupsCompositeForeignKeysInRowOrder() {
        when(jdbc.query(eq(OracleMetadataScanner.TABLE_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of("ACCOUNT"));
        when(jdbc.query(eq(OracleMetadataScanner.COLUMN_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.KEY_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(
                new OracleMetadataScanner.KeyRow("ACCOUNT", "FK_ACCOUNT_OWNER", "FOREIGN_KEY", "OWNER_ID", "OWNER", "ID"),
                new OracleMetadataScanner.KeyRow("ACCOUNT", "FK_ACCOUNT_OWNER", "FOREIGN_KEY", "OWNER_TYPE", "OWNER", "TYPE")
            ));
        when(jdbc.query(eq(OracleMetadataScanner.INDEX_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_DEPENDENCY_SQL), any(RowMapper.class), eq("BANK_CORE"), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());

        when(jdbc.query(contains("object_type in"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of());
        when(jdbc.query(contains("constraint_type = 'C'"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of());

        OracleMetadataScanner scanner = new OracleMetadataScanner(jdbc);

        MigrationManifest manifest = scanner.scan("run-002", "BaNk_CoRe");

        assertThat(manifest.tables()).hasSize(1);
        assertThat(manifest.tables().getFirst().keys()).hasSize(1);
        KeyMetadata key = manifest.tables().getFirst().keys().getFirst();
        assertThat(key.name()).isEqualTo("FK_ACCOUNT_OWNER");
        assertThat(key.type()).isEqualTo("FOREIGN_KEY");
        assertThat(key.columns()).containsExactly("OWNER_ID", "OWNER_TYPE");
        assertThat(key.referencedTable()).isEqualTo("OWNER");
        assertThat(key.referencedColumns()).containsExactly("ID", "TYPE");
        assertThat(manifest.sourceSchema()).isEqualTo("BANK_CORE");
    }

    @Test
    void coercesNumericMetadataThroughColumnRowMapper() throws Exception {
        when(resultSet.getString("table_name")).thenReturn("ACCOUNT");
        when(resultSet.getString("column_name")).thenReturn("ID");
        when(resultSet.getString("data_type")).thenReturn("NUMBER");
        when(resultSet.getObject("data_precision")).thenReturn(new BigDecimal("19"));
        when(resultSet.getObject("data_scale")).thenReturn(new BigDecimal("0"));
        when(resultSet.getString("nullable")).thenReturn("N");
        when(resultSet.getString("data_default")).thenReturn(null);

        OracleMetadataScanner.ColumnRow row = OracleMetadataScanner.COLUMN_ROW_MAPPER.mapRow(resultSet, 0);

        assertThat(row.precision()).isEqualTo(19);
        assertThat(row.scale()).isEqualTo(0);
        assertThat(row.nullable()).isEqualTo("N");
    }

    @Test
    void coercesNumericStringsAndRejectsUnsupportedNumericValues() {
        assertThat(OracleMetadataScanner.toInteger(" 42 ")).isEqualTo(42);
        assertThat(OracleMetadataScanner.toInteger(new BigDecimal("7"))).isEqualTo(7);
        assertThatThrownBy(() -> OracleMetadataScanner.toInteger(new Object()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported numeric metadata value type");
    }

    @Test
    @SuppressWarnings("unchecked")
    void scansExpressionBasedIndexAndReportsInventoryOnly() {
        when(jdbc.query(eq(OracleMetadataScanner.TABLE_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of("ACCOUNT"));
        when(jdbc.query(eq(OracleMetadataScanner.COLUMN_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(
                new OracleMetadataScanner.ColumnRow("ACCOUNT", "ID", "NUMBER", 19, 0, "N", null),
                new OracleMetadataScanner.ColumnRow("ACCOUNT", "NAME", "VARCHAR2", 50, null, "Y", null)
            ));
        when(jdbc.query(eq(OracleMetadataScanner.KEY_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.INDEX_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(
                new OracleMetadataScanner.IndexRow("ACCOUNT", "IX_ACCOUNT_NAME_UPPER", "NONUNIQUE", "SYS_NC00003$", "UPPER(\"NAME\")")
            ));
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_DEPENDENCY_SQL), any(RowMapper.class), eq("BANK_CORE"), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());

        when(jdbc.query(contains("object_type in"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of());
        when(jdbc.query(contains("constraint_type = 'C'"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of());

        OracleMetadataScanner scanner = new OracleMetadataScanner(jdbc);
        MigrationManifest manifest = scanner.scan("run-003", "bank_core");

        assertThat(manifest.tables()).hasSize(1);
        var table = manifest.tables().getFirst();
        assertThat(table.indexes()).isEmpty();

        assertThat(manifest.schemaObjects()).hasSize(1);
        var schemaObj = manifest.schemaObjects().getFirst();
        assertThat(schemaObj.name()).isEqualTo("IX_ACCOUNT_NAME_UPPER");
        assertThat(schemaObj.type()).isEqualTo(SchemaObjectType.FUNCTION_BASED_INDEX);
        assertThat(schemaObj.status()).isEqualTo(ObjectStatus.NEEDS_REVIEW);
    }

    @Test
    @SuppressWarnings("unchecked")
    void scansBroaderSchemaObjects() {
        when(jdbc.query(eq(OracleMetadataScanner.TABLE_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of("ACCOUNT"));
        when(jdbc.query(eq(OracleMetadataScanner.COLUMN_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.ColumnRow("ACCOUNT", "ID", "NUMBER", 19, 0, "N", null)));
        when(jdbc.query(eq(OracleMetadataScanner.KEY_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.INDEX_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(
                new OracleMetadataScanner.IndexRow("ACCOUNT", "IX_BITMAP", "NONUNIQUE", "ID", null, "BITMAP")
            ));
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_DEPENDENCY_SQL), any(RowMapper.class), eq("BANK_CORE"), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());

        when(jdbc.query(contains("object_type in"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of(
                Map.of("object_name", "SEQ_TEST", "object_type", "SEQUENCE", "status", "VALID"),
                Map.of("object_name", "SYN_TEST", "object_type", "SYNONYM", "status", "VALID"),
                Map.of("object_name", "TRG_TEST", "object_type", "TRIGGER", "status", "VALID")
            ));

        when(jdbc.query(contains("constraint_type = 'C'"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of(
                Map.of("constraint_name", "CK_TEST", "table_name", "ACCOUNT", "status", "ENABLED")
            ));

        OracleMetadataScanner scanner = new OracleMetadataScanner(jdbc);
        MigrationManifest manifest = scanner.scan("run-004", "bank_core");

        assertThat(manifest.tables().getFirst().indexes()).isEmpty();
        assertThat(manifest.schemaObjects()).hasSize(5); // SEQ_TEST, SYN_TEST, TRG_TEST, CK_TEST, IX_BITMAP
        assertThat(manifest.schemaObjects()).extracting("name").containsExactlyInAnyOrder(
            "SEQ_TEST", "SYN_TEST", "TRG_TEST", "CK_TEST", "IX_BITMAP"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void scansCheckConstraintsAndFiltersGeneratedNotNullConstraints() {
        when(jdbc.query(eq(OracleMetadataScanner.TABLE_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of("ACCOUNT"));
        when(jdbc.query(eq(OracleMetadataScanner.COLUMN_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.ColumnRow("ACCOUNT", "ID", "NUMBER", 19, 0, "N", null)));
        when(jdbc.query(eq(OracleMetadataScanner.KEY_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.INDEX_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_DEPENDENCY_SQL), any(RowMapper.class), eq("BANK_CORE"), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());

        when(jdbc.query(contains("object_type in"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of());

        // We return two constraints:
        // 1. A real check constraint: "CK_SALARY" -> "salary > 0"
        // 2. A generated not null constraint: "SYS_C001234" -> "\"ID\" IS NOT NULL"
        when(jdbc.query(contains("constraint_type = 'C'"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of(
                Map.of("constraint_name", "CK_SALARY", "table_name", "ACCOUNT", "status", "ENABLED", "search_condition", "salary > 0"),
                Map.of("constraint_name", "SYS_C001234", "table_name", "ACCOUNT", "status", "ENABLED", "search_condition", "\"ID\" IS NOT NULL")
            ));

        OracleMetadataScanner scanner = new OracleMetadataScanner(jdbc);
        MigrationManifest manifest = scanner.scan("run-005", "bank_core");

        // "SYS_C001234" should be filtered out, only "CK_SALARY" remains
        assertThat(manifest.schemaObjects()).hasSize(1);
        var obj = manifest.schemaObjects().getFirst();
        assertThat(obj.name()).isEqualTo("CK_SALARY");
        assertThat(obj.type()).isEqualTo(SchemaObjectType.CHECK_CONSTRAINT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void normalIndexesRemainInTableMetadataButSpecialIndexesAreInventoryOnly() {
        when(jdbc.query(eq(OracleMetadataScanner.TABLE_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of("ACCOUNT"));
        when(jdbc.query(eq(OracleMetadataScanner.COLUMN_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(
                new OracleMetadataScanner.ColumnRow("ACCOUNT", "ID", "NUMBER", 19, 0, "N", null),
                new OracleMetadataScanner.ColumnRow("ACCOUNT", "NAME", "VARCHAR2", 50, null, "Y", null)
            ));
        when(jdbc.query(eq(OracleMetadataScanner.KEY_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.INDEX_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(
                new OracleMetadataScanner.IndexRow("ACCOUNT", "IX_NORMAL", "NONUNIQUE", "ID", null, "NORMAL"),
                new OracleMetadataScanner.IndexRow("ACCOUNT", "IX_BITMAP", "NONUNIQUE", "NAME", null, "BITMAP"),
                new OracleMetadataScanner.IndexRow("ACCOUNT", "IX_FUNC", "NONUNIQUE", "SYS_NC00003$", "UPPER(\"NAME\")", "NORMAL")
            ));
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_DEPENDENCY_SQL), any(RowMapper.class), eq("BANK_CORE"), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of());

        when(jdbc.query(contains("object_type in"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of());
        when(jdbc.query(contains("constraint_type = 'C'"), any(RowMapper.class), eq("BANK_CORE")))
            .thenReturn(List.of());

        OracleMetadataScanner scanner = new OracleMetadataScanner(jdbc);
        MigrationManifest manifest = scanner.scan("run-006", "bank_core");

        // Verify normal indexes remain in TableMetadata
        assertThat(manifest.tables()).hasSize(1);
        var table = manifest.tables().getFirst();
        assertThat(table.indexes()).hasSize(1);
        assertThat(table.indexes().getFirst().name()).isEqualTo("IX_NORMAL");

        // Verify special indexes are added to schemaObjects (inventory/readiness reporting)
        assertThat(manifest.schemaObjects()).extracting("name")
            .containsExactlyInAnyOrder("IX_BITMAP", "IX_FUNC");

        var bitmapObj = manifest.schemaObjects().stream().filter(o -> o.name().equals("IX_BITMAP")).findFirst().orElseThrow();
        assertThat(bitmapObj.type()).isEqualTo(SchemaObjectType.BITMAP_INDEX);
        assertThat(bitmapObj.status()).isEqualTo(ObjectStatus.NEEDS_REVIEW);

        var funcObj = manifest.schemaObjects().stream().filter(o -> o.name().equals("IX_FUNC")).findFirst().orElseThrow();
        assertThat(funcObj.type()).isEqualTo(SchemaObjectType.FUNCTION_BASED_INDEX);
        assertThat(funcObj.status()).isEqualTo(ObjectStatus.NEEDS_REVIEW);
    }
}

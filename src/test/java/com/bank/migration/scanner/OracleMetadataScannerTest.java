package com.bank.migration.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import java.math.BigDecimal;
import java.util.List;
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
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.IndexRow("ACCOUNT", "IX_ACCOUNT_ID", "NONUNIQUE", "ID")));
        when(jdbc.query(eq(OracleMetadataScanner.VIEW_SQL), any(RowMapper.class), eq("BANK_CORE")))
            .thenAnswer(invocation -> List.of(new OracleMetadataScanner.ViewRow("VW_ACCOUNT", "select ID from ACCOUNT")));

        OracleMetadataScanner scanner = new OracleMetadataScanner(jdbc);

        MigrationManifest manifest = scanner.scan("run-001", "bank_core");

        assertThat(manifest.tables()).hasSize(1);
        assertThat(manifest.tables().getFirst().name()).isEqualTo("ACCOUNT");
        assertThat(manifest.tables().getFirst().status()).isEqualTo(ObjectStatus.READY);
        assertThat(manifest.tables().getFirst().columns()).hasSize(1);
        assertThat(manifest.tables().getFirst().schema()).isEqualTo("BANK_CORE");
        assertThat(manifest.views()).hasSize(1);
        assertThat(manifest.views().getFirst().name()).isEqualTo("VW_ACCOUNT");
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
}

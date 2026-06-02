package com.bank.migration.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

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

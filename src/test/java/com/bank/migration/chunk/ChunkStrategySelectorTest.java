package com.bank.migration.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChunkStrategySelectorTest {
    @Test
    void selectsNumericPrimaryKeyForSingleNumberPk() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "TEST_TABLE",
            ObjectStatus.READY,
            List.of(new ColumnMetadata("ID", "NUMBER", 18, 0, false, null)),
            List.of(new KeyMetadata("PK_TEST", "PRIMARY_KEY", List.of("ID"), null, null)),
            List.of()
        );
        assertThat(ChunkStrategySelector.select(table)).isEqualTo(ChunkStrategyType.NUMERIC_PRIMARY_KEY);
    }

    @Test
    void selectsRowNumberForSingleVarcharPk() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "TEST_TABLE",
            ObjectStatus.READY,
            List.of(new ColumnMetadata("CITYID", "VARCHAR2", 20, null, false, null)),
            List.of(new KeyMetadata("PK_TEST", "PRIMARY_KEY", List.of("CITYID"), null, null)),
            List.of()
        );
        assertThat(ChunkStrategySelector.select(table)).isEqualTo(ChunkStrategyType.ROW_NUMBER);
    }

    @Test
    void selectsRowNumberForCompositePk() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "TEST_TABLE",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID_A", "NUMBER", 18, 0, false, null),
                new ColumnMetadata("ID_B", "NUMBER", 18, 0, false, null)
            ),
            List.of(new KeyMetadata("PK_TEST", "PRIMARY_KEY", List.of("ID_A", "ID_B"), null, null)),
            List.of()
        );
        assertThat(ChunkStrategySelector.select(table)).isEqualTo(ChunkStrategyType.ROW_NUMBER);
    }

    @Test
    void selectsRowIdForNoPk() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "TEST_TABLE",
            ObjectStatus.READY,
            List.of(new ColumnMetadata("VAL", "VARCHAR2", 100, null, true, null)),
            List.of(),
            List.of()
        );
        assertThat(ChunkStrategySelector.select(table)).isEqualTo(ChunkStrategyType.ROWID);
    }
}

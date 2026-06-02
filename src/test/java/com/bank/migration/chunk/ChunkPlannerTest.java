package com.bank.migration.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

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
    void usesRowIdFallbackChunksWhenNoPrimaryKeyExists() {
        TableMetadata table = new TableMetadata("BANK_CORE", "AUDIT_LOG", ObjectStatus.WARNING, List.of(), List.of(), List.of());

        List<ChunkPlan> chunks = new ChunkPlanner().plan(table, 1L, 10_000L, 5_000);

        assertThat(chunks).containsExactly(
            new ChunkPlan(
                "AUDIT_LOG-000001",
                "ROWID",
                "ROWID in (select rid from (select ROWID rid, row_number() over (order by ROWID) rn from BANK_CORE.AUDIT_LOG) where rn >= 1 and rn <= 5000)",
                "ROWID_FALLBACK"
            ),
            new ChunkPlan(
                "AUDIT_LOG-000002",
                "ROWID",
                "ROWID in (select rid from (select ROWID rid, row_number() over (order by ROWID) rn from BANK_CORE.AUDIT_LOG) where rn >= 5001 and rn <= 10000)",
                "ROWID_FALLBACK"
            )
        );
    }

    @Test
    void rejectsInvalidChunkSize() {
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> new ChunkPlanner().plan(table, 1L, 100L, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Chunk size");
    }
}

package com.bank.migration.audit;

import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

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

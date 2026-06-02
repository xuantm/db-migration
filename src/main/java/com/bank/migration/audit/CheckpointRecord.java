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

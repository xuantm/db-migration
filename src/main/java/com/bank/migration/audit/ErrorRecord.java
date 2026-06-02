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

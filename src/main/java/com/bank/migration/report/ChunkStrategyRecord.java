package com.bank.migration.report;

public record ChunkStrategyRecord(
    String tableName,
    String chunkId,
    String strategy,
    String columnName,
    String whereClause
) {}

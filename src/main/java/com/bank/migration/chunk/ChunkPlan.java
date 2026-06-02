package com.bank.migration.chunk;

public record ChunkPlan(String chunkId, String columnName, String whereClause, String strategy) {}

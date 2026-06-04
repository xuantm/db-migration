package com.bank.migration.chunk;

import com.bank.migration.domain.TableMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

public interface ChunkStrategy {
    ChunkStrategyType type();
    ChunkBounds getBounds(JdbcTemplate sourceJdbc, TableMetadata table);
    List<ChunkPlan> plan(TableMetadata table, long minInclusive, long maxInclusive, int chunkSize);
}

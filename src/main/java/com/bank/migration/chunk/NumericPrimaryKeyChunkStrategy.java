package com.bank.migration.chunk;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.List;

public class NumericPrimaryKeyChunkStrategy implements ChunkStrategy {
    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.NUMERIC_PRIMARY_KEY;
    }

    @Override
    public ChunkBounds getBounds(JdbcTemplate sourceJdbc, TableMetadata table) {
        String pkCol = getPkColumn(table);
        Long min = sourceJdbc.queryForObject("select min(" + pkCol + ") from " + table.schema() + "." + table.name(), Long.class);
        Long max = sourceJdbc.queryForObject("select max(" + pkCol + ") from " + table.schema() + "." + table.name(), Long.class);
        return new ChunkBounds(min == null ? 1L : min, max == null ? 0L : max);
    }

    @Override
    public List<ChunkPlan> plan(TableMetadata table, long minInclusive, long maxInclusive, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be greater than zero");
        }
        if (maxInclusive < minInclusive) {
            return List.of();
        }
        String column = getPkColumn(table);
        List<ChunkPlan> chunks = new ArrayList<>();
        long start = minInclusive;
        int index = 1;
        while (start <= maxInclusive) {
            long end = Math.min(start + chunkSize - 1L, maxInclusive);
            String chunkId = "%s-%06d".formatted(table.name(), index);
            chunks.add(new ChunkPlan(
                chunkId,
                column,
                column + " >= " + start + " and " + column + " <= " + end,
                type().name()
            ));
            start = end + 1L;
            index++;
        }
        return List.copyOf(chunks);
    }

    private String getPkColumn(TableMetadata table) {
        return table.keys().stream()
            .filter(key -> "PRIMARY_KEY".equalsIgnoreCase(key.type()))
            .filter(key -> key.columns().size() == 1)
            .map(KeyMetadata::columns)
            .map(columns -> columns.getFirst())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Table " + table.name() + " has no single primary key"));
    }
}

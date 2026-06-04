package com.bank.migration.chunk;

import com.bank.migration.domain.TableMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.List;

public class RowIdChunkStrategy implements ChunkStrategy {
    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.ROWID;
    }

    @Override
    public ChunkBounds getBounds(JdbcTemplate sourceJdbc, TableMetadata table) {
        Long rowCount = sourceJdbc.queryForObject("select count(*) from " + table.schema() + "." + table.name(), Long.class);
        return new ChunkBounds(1L, rowCount == null ? 0L : rowCount);
    }

    @Override
    public List<ChunkPlan> plan(TableMetadata table, long minInclusive, long maxInclusive, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be greater than zero");
        }
        if (maxInclusive < minInclusive) {
            return List.of();
        }
        List<ChunkPlan> chunks = new ArrayList<>();
        long start = minInclusive;
        int index = 1;
        while (start <= maxInclusive) {
            long end = Math.min(start + chunkSize - 1L, maxInclusive);
            String chunkId = "%s-%06d".formatted(table.name(), index);
            chunks.add(new ChunkPlan(
                chunkId,
                "ROWID",
                "ROWID in (select rid from (select ROWID rid, row_number() over (order by ROWID) rn from "
                    + table.schema() + "." + table.name() + ") where rn >= " + start + " and rn <= " + end + ")",
                type().name()
            ));
            start = end + 1L;
            index++;
        }
        return List.copyOf(chunks);
    }
}

package com.bank.migration.chunk;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RowNumberChunkStrategy implements ChunkStrategy {
    @Override
    public ChunkStrategyType type() {
        return ChunkStrategyType.ROW_NUMBER;
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

        Optional<KeyMetadata> primaryKey = table.keys().stream()
            .filter(key -> "PRIMARY_KEY".equalsIgnoreCase(key.type()))
            .findFirst();

        String orderBy;
        if (primaryKey.isPresent()) {
            orderBy = String.join(", ", primaryKey.get().columns());
        } else {
            orderBy = "ROWID";
        }

        while (start <= maxInclusive) {
            long end = Math.min(start + chunkSize - 1L, maxInclusive);
            String chunkId = "%s-%06d".formatted(table.name(), index);
            chunks.add(new ChunkPlan(
                chunkId,
                "ROWID",
                "ROWID in (select rid from (select ROWID rid, row_number() over (order by " + orderBy + ") rn from "
                    + table.schema() + "." + table.name() + ") where rn >= " + start + " and rn <= " + end + ")",
                type().name()
            ));
            start = end + 1L;
            index++;
        }
        return List.copyOf(chunks);
    }
}

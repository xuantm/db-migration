package com.bank.migration.chunk;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChunkPlanner {
    public List<ChunkPlan> plan(TableMetadata table, long minInclusive, long maxInclusive, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be greater than zero");
        }

        Optional<KeyMetadata> primaryKey = table.keys().stream()
            .filter(key -> "PRIMARY_KEY".equals(key.type()))
            .filter(key -> key.columns().size() == 1)
            .findFirst();

        if (primaryKey.isEmpty()) {
            return List.of(new ChunkPlan(table.name() + "-000001", "ROWID", "1 = 1", "ROWID_FALLBACK"));
        }

        if (maxInclusive < minInclusive) {
            return List.of();
        }

        String column = primaryKey.get().columns().getFirst();
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
                "PRIMARY_KEY_RANGE"
            ));
            start = end + 1L;
            index++;
        }
        return List.copyOf(chunks);
    }
}

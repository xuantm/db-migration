package com.bank.migration.chunk;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ChunkPlanner {
    public List<ChunkPlan> plan(TableMetadata table, long minInclusive, long maxInclusive, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be greater than zero");
        }

        if (maxInclusive < minInclusive) {
            return List.of();
        }

        Optional<KeyMetadata> primaryKey = table.keys().stream()
            .filter(key -> "PRIMARY_KEY".equalsIgnoreCase(key.type()))
            .filter(key -> key.columns().size() == 1)
            .findFirst();

        if (primaryKey.isEmpty()) {
            return planRowIdFallback(table, minInclusive, maxInclusive, chunkSize);
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

    private static List<ChunkPlan> planRowIdFallback(TableMetadata table, long minInclusive, long maxInclusive, int chunkSize) {
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
                    + table.schema()
                    + "."
                    + table.name()
                    + ") where rn >= "
                    + start
                    + " and rn <= "
                    + end
                    + ")",
                "ROWID_FALLBACK"
            ));
            start = end + 1L;
            index++;
        }
        return List.copyOf(chunks);
    }
}

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
        ChunkStrategyType strategyType = ChunkStrategySelector.select(table);
        ChunkStrategy strategy = ChunkStrategySelector.getStrategy(strategyType);
        return strategy.plan(table, minInclusive, maxInclusive, chunkSize);
    }
}

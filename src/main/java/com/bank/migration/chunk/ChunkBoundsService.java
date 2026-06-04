package com.bank.migration.chunk;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChunkBoundsService {
    private final JdbcTemplate sourceJdbc;

    public ChunkBoundsService(@Qualifier("sourceJdbc") JdbcTemplate sourceJdbc) {
        this.sourceJdbc = sourceJdbc;
    }

    public ChunkBounds bounds(TableMetadata table) {
        ChunkStrategyType strategyType = ChunkStrategySelector.select(table);
        ChunkStrategy strategy = ChunkStrategySelector.getStrategy(strategyType);
        return strategy.getBounds(sourceJdbc, table);
    }
}

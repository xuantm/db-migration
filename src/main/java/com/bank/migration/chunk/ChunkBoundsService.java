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
        String primaryKeyColumn = table.keys().stream()
            .filter(key -> "PRIMARY_KEY".equalsIgnoreCase(key.type()))
            .filter(key -> key.columns().size() == 1)
            .map(KeyMetadata::columns)
            .map(columns -> columns.getFirst())
            .findFirst()
            .orElse(null);

        if (primaryKeyColumn == null) {
            Long rowCount = sourceJdbc.queryForObject("select count(*) from " + table.schema() + "." + table.name(), Long.class);
            return new ChunkBounds(1L, rowCount == null ? 0L : rowCount);
        }

        Long min = sourceJdbc.queryForObject("select min(" + primaryKeyColumn + ") from " + table.schema() + "." + table.name(), Long.class);
        Long max = sourceJdbc.queryForObject("select max(" + primaryKeyColumn + ") from " + table.schema() + "." + table.name(), Long.class);
        return new ChunkBounds(min == null ? 1L : min, max == null ? 0L : max);
    }
}

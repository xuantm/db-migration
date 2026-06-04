package com.bank.migration.chunk;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import java.util.Optional;

public class ChunkStrategySelector {
    public static ChunkStrategyType select(TableMetadata table) {
        Optional<KeyMetadata> primaryKeyOpt = table.keys().stream()
            .filter(key -> "PRIMARY_KEY".equalsIgnoreCase(key.type()))
            .findFirst();

        if (primaryKeyOpt.isEmpty()) {
            return ChunkStrategyType.ROWID;
        }

        KeyMetadata primaryKey = primaryKeyOpt.get();
        if (primaryKey.columns().size() > 1) {
            return ChunkStrategyType.ROW_NUMBER;
        }

        String pkColumnName = primaryKey.columns().getFirst();
        Optional<ColumnMetadata> colOpt = table.columns().stream()
            .filter(c -> c.name().equalsIgnoreCase(pkColumnName))
            .findFirst();

        if (colOpt.isPresent()) {
            ColumnMetadata col = colOpt.get();
            String type = col.oracleType() == null ? "" : col.oracleType().toUpperCase();
            if (type.equals("NUMBER")) {
                Integer scale = col.scale();
                if (scale == null || scale == 0) {
                    return ChunkStrategyType.NUMERIC_PRIMARY_KEY;
                }
            }
        }

        return ChunkStrategyType.ROW_NUMBER;
    }

    public static ChunkStrategy getStrategy(ChunkStrategyType type) {
        return switch (type) {
            case NUMERIC_PRIMARY_KEY -> new NumericPrimaryKeyChunkStrategy();
            case ROW_NUMBER -> new RowNumberChunkStrategy();
            case ROWID -> new RowIdChunkStrategy();
        };
    }
}

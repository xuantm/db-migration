package com.bank.migration.domain;

import java.util.List;

public record TableMetadata(
    String schema,
    String name,
    ObjectStatus status,
    List<ColumnMetadata> columns,
    List<KeyMetadata> keys,
    List<IndexMetadata> indexes
) {
    public TableMetadata {
        columns = List.copyOf(columns == null ? List.of() : columns);
        keys = List.copyOf(keys == null ? List.of() : keys);
        indexes = List.copyOf(indexes == null ? List.of() : indexes);
    }
}

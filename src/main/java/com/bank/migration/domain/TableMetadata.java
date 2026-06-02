package com.bank.migration.domain;

import java.util.List;

public record TableMetadata(
    String schema,
    String name,
    ObjectStatus status,
    List<ColumnMetadata> columns,
    List<KeyMetadata> keys,
    List<IndexMetadata> indexes
) {}

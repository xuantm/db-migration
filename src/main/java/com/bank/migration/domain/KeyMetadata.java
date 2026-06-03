package com.bank.migration.domain;

import java.util.List;

public record KeyMetadata(
    String name,
    String type,
    List<String> columns,
    String referencedTable,
    List<String> referencedColumns,
    boolean validated
) {
    public KeyMetadata(String name, String type, List<String> columns, String referencedTable, List<String> referencedColumns) {
        this(name, type, columns, referencedTable, referencedColumns, true);
    }

    public KeyMetadata {
        columns = List.copyOf(columns == null ? List.of() : columns);
        referencedColumns = List.copyOf(referencedColumns == null ? List.of() : referencedColumns);
    }
}

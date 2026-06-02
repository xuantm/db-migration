package com.bank.migration.domain;

import java.util.List;

public record MigrationManifest(
    String runId,
    String sourceSchema,
    List<TableMetadata> tables,
    List<ViewMetadata> views
) {
    public MigrationManifest {
        tables = List.copyOf(tables == null ? List.of() : tables);
        views = List.copyOf(views == null ? List.of() : views);
    }
}

package com.bank.migration.domain;

import java.util.List;

public record MigrationManifest(
    String runId,
    String sourceSchema,
    List<TableMetadata> tables,
    List<ViewMetadata> views,
    List<SchemaObjectMetadata> schemaObjects
) {
    public MigrationManifest(String runId, String sourceSchema, List<TableMetadata> tables, List<ViewMetadata> views) {
        this(runId, sourceSchema, tables, views, List.of());
    }

    public MigrationManifest {
        tables = List.copyOf(tables == null ? List.of() : tables);
        views = List.copyOf(views == null ? List.of() : views);
        schemaObjects = List.copyOf(schemaObjects == null ? List.of() : schemaObjects);
    }
}

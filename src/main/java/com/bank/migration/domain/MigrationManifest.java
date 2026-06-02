package com.bank.migration.domain;

import java.util.List;

public record MigrationManifest(
    String runId,
    String sourceSchema,
    List<TableMetadata> tables,
    List<ViewMetadata> views
) {}

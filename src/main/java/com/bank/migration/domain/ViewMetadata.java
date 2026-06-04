package com.bank.migration.domain;

import java.util.List;

public record ViewMetadata(
    String schema,
    String name,
    ObjectStatus status,
    String sql,
    List<String> dependencies,
    List<String> notes,
    String exclusionReason
) {
    public ViewMetadata {
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        notes = List.copyOf(notes == null ? List.of() : notes);
    }

    public ViewMetadata(
        String schema,
        String name,
        ObjectStatus status,
        String sql,
        List<String> dependencies,
        List<String> notes
    ) {
        this(schema, name, status, sql, dependencies, notes, null);
    }
}

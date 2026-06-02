package com.bank.migration.domain;

import java.util.List;

public record ViewMetadata(
    String schema,
    String name,
    ObjectStatus status,
    String sql,
    List<String> dependencies,
    List<String> notes
) {
    public ViewMetadata {
        dependencies = List.copyOf(dependencies == null ? List.of() : dependencies);
        notes = List.copyOf(notes == null ? List.of() : notes);
    }
}

package com.bank.migration.domain;

import java.util.List;

public record SchemaObjectMetadata(
    String owner,
    String name,
    SchemaObjectType type,
    ObjectStatus status,
    List<String> notes,
    String exclusionReason
) {
    public SchemaObjectMetadata {
        notes = List.copyOf(notes == null ? List.of() : notes);
    }

    public SchemaObjectMetadata(
        String owner,
        String name,
        SchemaObjectType type,
        ObjectStatus status,
        List<String> notes
    ) {
        this(owner, name, type, status, notes, null);
    }
}

package com.bank.migration.types;

import java.util.List;

public record TypeMappingResult(
    String targetSqlType,
    TypeRisk risk,
    List<String> notes,
    boolean canGenerateDdl
) {
    public TypeMappingResult {
        notes = List.copyOf(notes == null ? List.of() : notes);
    }
}

package com.bank.migration.ddl;

import java.util.List;

public record GaussType(String sqlType, boolean needsReview, List<String> notes) {
    public GaussType {
        notes = List.copyOf(notes == null ? List.of() : notes);
    }
}

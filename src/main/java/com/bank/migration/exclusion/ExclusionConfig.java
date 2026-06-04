package com.bank.migration.exclusion;

import java.util.List;

public record ExclusionConfig(
    List<String> tables,
    List<String> views,
    List<String> sequences
) {
    public ExclusionConfig {
        tables = List.copyOf(tables == null ? List.of() : tables);
        views = List.copyOf(views == null ? List.of() : views);
        sequences = List.copyOf(sequences == null ? List.of() : sequences);
    }
}

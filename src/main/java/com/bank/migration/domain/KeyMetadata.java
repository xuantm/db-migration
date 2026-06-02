package com.bank.migration.domain;

import java.util.List;

public record KeyMetadata(
    String name,
    String type,
    List<String> columns,
    String referencedTable,
    List<String> referencedColumns
) {}

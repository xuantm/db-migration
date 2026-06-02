package com.bank.migration.domain;

import java.util.List;

public record ViewMetadata(
    String schema,
    String name,
    ObjectStatus status,
    String sql,
    List<String> dependencies,
    List<String> notes
) {}

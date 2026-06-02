package com.bank.migration.domain;

public record ColumnMetadata(
    String name,
    String oracleType,
    Integer precision,
    Integer scale,
    boolean nullable,
    String defaultExpression
) {}

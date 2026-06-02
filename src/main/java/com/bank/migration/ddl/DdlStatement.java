package com.bank.migration.ddl;

public record DdlStatement(String phase, String objectName, String sql) {}

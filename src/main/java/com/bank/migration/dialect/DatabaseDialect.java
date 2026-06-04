package com.bank.migration.dialect;

public interface DatabaseDialect {
    String renderIdentifier(String rawIdentifier);
    String renderQualifiedName(String schema, String objectName);
    boolean isReservedWord(String identifier);
}

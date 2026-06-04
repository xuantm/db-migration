package com.bank.migration.readiness;

public record ReadinessFinding(
    String code,
    ReadinessSeverity severity,
    String objectType,
    String objectName,
    String columnName,
    String description,
    String detectionMethod,
    String mitigation,
    boolean shouldStop
) {}

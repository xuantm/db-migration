package com.bank.migration.validate;

public record ValidationResult(String name, ValidationStatus status, String objectName, String message) {}

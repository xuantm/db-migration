package com.bank.migration.report;

public record ExcludedObjectDecision(
    String objectType,   // "TABLE" or "VIEW"
    String objectName,
    String status,       // "EXCLUDED" or "NEEDS_REVIEW"
    String reason        // exclusionReason / decision note
) {
}

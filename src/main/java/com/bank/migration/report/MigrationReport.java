package com.bank.migration.report;

import com.bank.migration.audit.ErrorRecord;
import com.bank.migration.validate.ValidationResult;
import java.time.Instant;
import java.util.List;

public record MigrationReport(
    String runId,
    String status,
    Instant startedAt,
    Instant completedAt,
    List<ValidationResult> validations,
    List<ErrorRecord> errors
) {
    public MigrationReport {
        validations = List.copyOf(validations == null ? List.of() : validations);
        errors = List.copyOf(errors == null ? List.of() : errors);
    }
}

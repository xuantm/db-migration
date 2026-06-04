package com.bank.migration.report;

import com.bank.migration.audit.ErrorRecord;
import com.bank.migration.readiness.ReadinessFinding;
import com.bank.migration.validate.ValidationResult;
import java.time.Instant;
import java.util.List;

public record MigrationReport(
    String runId,
    String status,
    String mode,
    List<String> skippedPhases,
    Instant startedAt,
    Instant completedAt,
    List<ValidationResult> validations,
    List<ErrorRecord> errors,
    List<ReadinessFinding> readinessFindings,
    List<ExcludedObjectDecision> excludedObjectDecisions,
    List<ChunkStrategyRecord> chunkStrategies
) {
    public MigrationReport(
        String runId,
        String status,
        Instant startedAt,
        Instant completedAt,
        List<ValidationResult> validations,
        List<ErrorRecord> errors
    ) {
        this(runId, status, "FULL", List.of(), startedAt, completedAt, validations, errors, List.of(), List.of(), List.of());
    }

    public MigrationReport(
        String runId,
        String status,
        Instant startedAt,
        Instant completedAt,
        List<ValidationResult> validations,
        List<ErrorRecord> errors,
        List<ReadinessFinding> readinessFindings
    ) {
        this(runId, status, "FULL", List.of(), startedAt, completedAt, validations, errors, readinessFindings, List.of(), List.of());
    }

    public MigrationReport(
        String runId,
        String status,
        Instant startedAt,
        Instant completedAt,
        List<ValidationResult> validations,
        List<ErrorRecord> errors,
        List<ReadinessFinding> readinessFindings,
        List<ExcludedObjectDecision> excludedObjectDecisions
    ) {
        this(runId, status, "FULL", List.of(), startedAt, completedAt, validations, errors, readinessFindings, excludedObjectDecisions, List.of());
    }

    public MigrationReport(
        String runId,
        String status,
        Instant startedAt,
        Instant completedAt,
        List<ValidationResult> validations,
        List<ErrorRecord> errors,
        List<ReadinessFinding> readinessFindings,
        List<ExcludedObjectDecision> excludedObjectDecisions,
        List<ChunkStrategyRecord> chunkStrategies
    ) {
        this(runId, status, "FULL", List.of(), startedAt, completedAt, validations, errors, readinessFindings, excludedObjectDecisions, chunkStrategies);
    }

    public MigrationReport {
        mode = mode == null || mode.isBlank() ? "FULL" : mode;
        skippedPhases = List.copyOf(skippedPhases == null ? List.of() : skippedPhases);
        validations = List.copyOf(validations == null ? List.of() : validations);
        errors = List.copyOf(errors == null ? List.of() : errors);
        readinessFindings = List.copyOf(readinessFindings == null ? List.of() : readinessFindings);
        excludedObjectDecisions = List.copyOf(excludedObjectDecisions == null ? List.of() : excludedObjectDecisions);
        chunkStrategies = List.copyOf(chunkStrategies == null ? List.of() : chunkStrategies);
    }
}

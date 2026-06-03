package com.bank.migration.orchestrator;

import com.bank.migration.audit.AuditSchemaService;
import com.bank.migration.audit.ErrorLogStore;
import com.bank.migration.audit.ErrorRecord;
import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.chunk.ChunkBounds;
import com.bank.migration.chunk.ChunkBoundsService;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.chunk.ChunkPlanner;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.DdlStatement;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.load.ChunkMigrationException;
import com.bank.migration.load.DataCopyService;
import com.bank.migration.load.TableMigrationTasklet;
import com.bank.migration.preflight.PreflightCheck;
import com.bank.migration.preflight.PreflightService;
import com.bank.migration.report.MigrationReport;
import com.bank.migration.report.ReportWriter;
import com.bank.migration.scanner.OracleMetadataScanner;
import com.bank.migration.validate.ValidationCoordinator;
import com.bank.migration.validate.ValidationResult;
import com.bank.migration.validate.ValidationStatus;
import com.bank.migration.view.ViewApplier;
import com.bank.migration.view.ViewPlan;
import com.bank.migration.view.ViewPlanner;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class MigrationOrchestrator {
    private final PreflightService preflightService;
    private final AuditSchemaService auditSchemaService;
    private final OracleMetadataScanner scanner;
    private final TargetSchemaService targetSchemaService;
    private final TableDdlPlanner ddlPlanner;
    private final DdlApplier ddlApplier;
    private final ChunkBoundsService chunkBoundsService;
    private final ChunkPlanner chunkPlanner;
    private final DataCopyService dataCopyService;
    private final CheckpointStore checkpointStore;
    private final ErrorLogStore errorLogStore;
    private final ValidationCoordinator validationCoordinator;
    private final ViewPlanner viewPlanner;
    private final ViewApplier viewApplier;
    private final ReportWriter reportWriter;

    public MigrationOrchestrator(
        PreflightService preflightService,
        AuditSchemaService auditSchemaService,
        OracleMetadataScanner scanner,
        TargetSchemaService targetSchemaService,
        TableDdlPlanner ddlPlanner,
        DdlApplier ddlApplier,
        ChunkBoundsService chunkBoundsService,
        ChunkPlanner chunkPlanner,
        DataCopyService dataCopyService,
        CheckpointStore checkpointStore,
        ErrorLogStore errorLogStore,
        ValidationCoordinator validationCoordinator,
        ViewPlanner viewPlanner,
        ViewApplier viewApplier,
        ReportWriter reportWriter
    ) {
        this.preflightService = preflightService;
        this.auditSchemaService = auditSchemaService;
        this.scanner = scanner;
        this.targetSchemaService = targetSchemaService;
        this.ddlPlanner = ddlPlanner;
        this.ddlApplier = ddlApplier;
        this.chunkBoundsService = chunkBoundsService;
        this.chunkPlanner = chunkPlanner;
        this.dataCopyService = dataCopyService;
        this.checkpointStore = checkpointStore;
        this.errorLogStore = errorLogStore;
        this.validationCoordinator = validationCoordinator;
        this.viewPlanner = viewPlanner;
        this.viewApplier = viewApplier;
        this.reportWriter = reportWriter;
    }

    public void run(MigrationProperties properties) throws Exception {
        Instant started = Instant.now();
        String runId = "run-" + UUID.randomUUID();
        String targetSchema = properties.target().schema();
        List<ValidationResult> validations = new ArrayList<>();
        List<ErrorRecord> errors = new ArrayList<>();
        boolean auditReady = false;

        try {
            ensurePreflightPassed(preflightService.run(targetSchema, properties.cleanLoad()));
            auditSchemaService.ensureAuditSchema();
            auditReady = true;

            MigrationManifest manifest = scanner.scan(runId, properties.source().schema());
            if (properties.cleanLoad()) {
                targetSchemaService.prepareCleanSchema(targetSchema);
            }

            List<DdlStatement> allDdl = planDdl(targetSchema, manifest);
            ddlApplier.apply(phase(allDdl, "TABLE"));

            for (TableMetadata table : manifest.tables()) {
                ChunkBounds bounds = chunkBoundsService.bounds(table);
                List<ChunkPlan> chunks = chunkPlanner.plan(table, bounds.minInclusive(), bounds.maxInclusive(), properties.batch().chunkSize());
                new TableMigrationTasklet(runId, targetSchema, table, chunks, dataCopyService, checkpointStore, errorLogStore).run();
            }

            List<DdlStatement> constraints = phase(allDdl, "CONSTRAINT");
            if (!constraints.isEmpty()) {
                ddlApplier.apply(constraints);
            }
            List<DdlStatement> indexes = phase(allDdl, "INDEX");
            if (!indexes.isEmpty()) {
                ddlApplier.apply(indexes);
            }
            List<DdlStatement> fks = phase(allDdl, "FOREIGN_KEY");
            if (!fks.isEmpty()) {
                ddlApplier.apply(fks);
            }

            List<ViewPlan> viewPlans = manifest.views().stream()
                .map(view -> viewPlanner.plan(targetSchema, view))
                .toList();
            viewApplier.applyReadyViews(targetSchema, viewPlans);

            validations.addAll(validationCoordinator.validate(manifest, targetSchema));
            reportWriter.write(new MigrationReport(
                runId,
                status(validations, viewPlans),
                started,
                Instant.now(),
                validations,
                errors
            ), Path.of(properties.reports().outputDir()));
        } catch (Exception ex) {
            ErrorRecord error = errorFor(runId, ex);
            errors.add(error);
            if (auditReady && !(ex instanceof ChunkMigrationException)) {
                saveErrorSafely(error);
            }
            try {
                reportWriter.write(new MigrationReport(runId, "FAIL", started, Instant.now(), validations, errors), Path.of(properties.reports().outputDir()));
            } catch (Exception reportException) {
                ex.addSuppressed(reportException);
            }
            throw ex;
        }
    }

    private static void ensurePreflightPassed(List<PreflightCheck> checks) {
        for (PreflightCheck check : checks) {
            if (!check.passed()) {
                throw new IllegalStateException("Preflight failed: " + check.name() + " - " + check.message());
            }
        }
    }

    private List<DdlStatement> planDdl(String targetSchema, MigrationManifest manifest) {
        List<DdlStatement> statements = new ArrayList<>();
        for (TableMetadata table : manifest.tables()) {
            statements.addAll(ddlPlanner.plan(targetSchema, table));
        }
        return List.copyOf(statements);
    }

    private static List<DdlStatement> phase(List<DdlStatement> statements, String phase) {
        return statements.stream()
            .filter(statement -> phase.equals(statement.phase()))
            .toList();
    }

    private static String status(List<ValidationResult> validations, List<ViewPlan> viewPlans) {
        boolean validationFailed = validations.stream().anyMatch(result -> result.status() == ValidationStatus.FAIL);
        if (validationFailed) {
            return "FAIL";
        }
        boolean viewNeedsReview = viewPlans.stream().anyMatch(plan -> plan.status() == ObjectStatus.NEEDS_REVIEW);
        return viewNeedsReview ? "WARNING" : "PASS";
    }

    private ErrorRecord errorRecord(String runId, Exception ex) {
        return new ErrorRecord(
            "err-" + UUID.randomUUID(),
            runId,
            "orchestration",
            "MIGRATION",
            null,
            null,
            null,
            databaseCode(ex),
            ex.getClass().getName() + ": " + ex.getMessage(),
            "MANUAL_REVIEW_REQUIRED",
            Instant.now()
        );
    }

    private ErrorRecord errorFor(String runId, Exception ex) {
        if (ex instanceof ChunkMigrationException chunkMigrationException) {
            return chunkMigrationException.errorRecord();
        }
        return errorRecord(runId, ex);
    }

    private void saveErrorSafely(ErrorRecord error) {
        try {
            errorLogStore.save(error);
        } catch (DataAccessException ignored) {
            // The filesystem report still carries the error if database audit logging fails.
        }
    }

    private static String databaseCode(Exception ex) {
        if (ex instanceof DataAccessException dataAccessException && dataAccessException.getMostSpecificCause() != null) {
            return dataAccessException.getMostSpecificCause().getClass().getSimpleName();
        }
        return null;
    }
}

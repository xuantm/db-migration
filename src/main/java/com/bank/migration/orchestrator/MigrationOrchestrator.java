package com.bank.migration.orchestrator;

import com.bank.migration.audit.AuditSchemaService;
import com.bank.migration.audit.ErrorLogStore;
import com.bank.migration.audit.ErrorRecord;
import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.audit.RunStatusStore;
import com.bank.migration.audit.PhaseStatusStore;
import com.bank.migration.chunk.ChunkBounds;
import com.bank.migration.chunk.ChunkBoundsService;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.chunk.ChunkPlanner;
import com.bank.migration.chunk.ChunkStrategySelector;
import com.bank.migration.report.ChunkStrategyRecord;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.DdlStatement;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.SchemaObjectMetadata;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.domain.ViewMetadata;
import com.bank.migration.load.ChunkMigrationException;
import com.bank.migration.report.ExcludedObjectDecision;
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
import com.bank.migration.exclusion.ExclusionConfig;
import com.bank.migration.exclusion.ExclusionResolver;
import com.bank.migration.readiness.ReadinessEvaluator;
import com.bank.migration.readiness.ReadinessFinding;
import com.bank.migration.readiness.ReadinessReport;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import com.bank.migration.manifest.ManifestCacheService;
import java.util.Optional;

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
    private final ReadinessEvaluator readinessEvaluator;
    private final ManifestCacheService manifestCacheService;
    private final RunStatusStore runStatusStore;
    private final PhaseStatusStore phaseStatusStore;

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
        ReportWriter reportWriter,
        ReadinessEvaluator readinessEvaluator
    ) {
        this(preflightService, auditSchemaService, scanner, targetSchemaService, ddlPlanner, ddlApplier,
             chunkBoundsService, chunkPlanner, dataCopyService, checkpointStore, errorLogStore,
             validationCoordinator, viewPlanner, viewApplier, reportWriter, readinessEvaluator, null, null, null);
    }

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
        ReportWriter reportWriter,
        ReadinessEvaluator readinessEvaluator,
        ManifestCacheService manifestCacheService
    ) {
        this(preflightService, auditSchemaService, scanner, targetSchemaService, ddlPlanner, ddlApplier,
             chunkBoundsService, chunkPlanner, dataCopyService, checkpointStore, errorLogStore,
             validationCoordinator, viewPlanner, viewApplier, reportWriter, readinessEvaluator,
             manifestCacheService, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
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
        ReportWriter reportWriter,
        ReadinessEvaluator readinessEvaluator,
        ManifestCacheService manifestCacheService,
        RunStatusStore runStatusStore,
        PhaseStatusStore phaseStatusStore
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
        this.readinessEvaluator = readinessEvaluator;
        this.manifestCacheService = manifestCacheService;
        this.runStatusStore = runStatusStore;
        this.phaseStatusStore = phaseStatusStore;
    }

    public void run(MigrationProperties properties) throws Exception {
        Instant started = Instant.now();
        String runId = "run-" + UUID.randomUUID();
        String targetSchema = properties.target().schema();
        List<ValidationResult> validations = new ArrayList<>();
        List<ErrorRecord> errors = new ArrayList<>();
        List<ReadinessFinding> readinessFindings = new ArrayList<>();
        boolean auditReady = false;
        MigrationManifest manifest = null;
        String currentPhase = null;
        List<ViewPlan> viewPlans = new ArrayList<>();
        List<ChunkStrategyRecord> chunkStrategies = new ArrayList<>();

        try {
            ensurePreflightPassed(preflightService.run(targetSchema, properties.cleanLoad()));
            auditSchemaService.ensureAuditSchema();
            auditReady = true;

            if (runStatusStore != null) {
                runStatusStore.startRun(runId, properties.source().schema(), targetSchema, started);
            }

            // Phase 1: metadata-scan
            currentPhase = "metadata-scan";
            if (phaseStatusStore != null) {
                phaseStatusStore.startPhase(runId, currentPhase, Instant.now());
            }

            Optional<MigrationManifest> cachedManifest = Optional.empty();
            if (manifestCacheService != null) {
                cachedManifest = manifestCacheService.load(properties);
            }

            if (cachedManifest.isPresent()) {
                manifest = cachedManifest.get();
            } else {
                manifest = scanner.scan(runId, properties.source().schema());
                if (manifestCacheService != null) {
                    manifestCacheService.save(properties, manifest);
                }
            }

            if (phaseStatusStore != null) {
                phaseStatusStore.finishPhase(runId, currentPhase, Instant.now(), "SUCCESS", null, "Manifest loaded successfully");
            }
            currentPhase = null;

            ExclusionConfig exclusionConfig = new ExclusionConfig(
                properties.excluded().tables(),
                properties.excluded().views(),
                properties.excluded().sequences()
            );
            ExclusionResolver exclusionResolver = new ExclusionResolver(exclusionConfig);
            manifest = exclusionResolver.resolve(manifest);

            // Phase 2: readiness-evaluation
            currentPhase = "readiness-evaluation";
            if (phaseStatusStore != null) {
                phaseStatusStore.startPhase(runId, currentPhase, Instant.now());
            }

            ReadinessReport readinessReport = readinessEvaluator.evaluate(manifest, properties);
            readinessFindings.addAll(readinessReport.findings());
            if (readinessReport.shouldStop()) {
                if (phaseStatusStore != null) {
                    phaseStatusStore.finishPhase(runId, currentPhase, Instant.now(), "FAILED", null, "Readiness evaluation failed");
                }
                throw new IllegalStateException("Readiness evaluation failed with blocking findings");
            }

            if (phaseStatusStore != null) {
                phaseStatusStore.finishPhase(runId, currentPhase, Instant.now(), "SUCCESS", null, "Readiness evaluation completed");
            }
            currentPhase = null;

            // Phase 3: ddl-application
            currentPhase = "ddl-application";
            if (phaseStatusStore != null) {
                phaseStatusStore.startPhase(runId, currentPhase, Instant.now());
            }

            if (properties.cleanLoad()) {
                targetSchemaService.prepareCleanSchema(targetSchema);
            }

            List<DdlStatement> allDdl = planDdl(targetSchema, manifest);
            List<DdlStatement> tablesDdl = phase(allDdl, "TABLE");
            ddlApplier.apply(tablesDdl);

            if (phaseStatusStore != null) {
                phaseStatusStore.finishPhase(runId, currentPhase, Instant.now(), "SUCCESS", (long) tablesDdl.size(), "Table DDL applied");
            }
            currentPhase = null;

            // Phase 4: data-load
            currentPhase = "data-load";
            if (phaseStatusStore != null) {
                phaseStatusStore.startPhase(runId, currentPhase, Instant.now());
            }

            long totalTablesLoaded = 0;
            for (TableMetadata table : manifest.tables()) {
                if (table.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                ChunkBounds bounds = chunkBoundsService.bounds(table);
                List<ChunkPlan> chunks = chunkPlanner.plan(table, bounds.minInclusive(), bounds.maxInclusive(), properties.batch().chunkSize());
                
                String strategyName = ChunkStrategySelector.select(table).name();
                if (chunks.isEmpty()) {
                    chunkStrategies.add(new ChunkStrategyRecord(
                        table.name(),
                        null,
                        strategyName,
                        null,
                        null
                    ));
                } else {
                    for (ChunkPlan chunk : chunks) {
                        chunkStrategies.add(new ChunkStrategyRecord(
                            table.name(),
                            chunk.chunkId(),
                            chunk.strategy(),
                            chunk.columnName(),
                            chunk.whereClause()
                        ));
                    }
                }

                new TableMigrationTasklet(runId, targetSchema, table, chunks, dataCopyService, checkpointStore, errorLogStore).run();
                totalTablesLoaded++;
            }

            if (phaseStatusStore != null) {
                phaseStatusStore.finishPhase(runId, currentPhase, Instant.now(), "SUCCESS", totalTablesLoaded, "Data loaded for " + totalTablesLoaded + " tables");
            }
            currentPhase = null;

            // Phase 5: constraints-and-indexes
            currentPhase = "constraints-and-indexes";
            if (phaseStatusStore != null) {
                phaseStatusStore.startPhase(runId, currentPhase, Instant.now());
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

            if (phaseStatusStore != null) {
                phaseStatusStore.finishPhase(runId, currentPhase, Instant.now(), "SUCCESS", (long) (constraints.size() + indexes.size() + fks.size()), "Constraints and indexes applied");
            }
            currentPhase = null;

            // Phase 6: view-application
            currentPhase = "view-application";
            if (phaseStatusStore != null) {
                phaseStatusStore.startPhase(runId, currentPhase, Instant.now());
            }

            viewPlans = manifest.views().stream()
                .map(view -> viewPlanner.plan(targetSchema, view))
                .toList();
            viewApplier.applyReadyViews(targetSchema, viewPlans);

            if (phaseStatusStore != null) {
                phaseStatusStore.finishPhase(runId, currentPhase, Instant.now(), "SUCCESS", (long) viewPlans.size(), "Views applied");
            }
            currentPhase = null;

            // Phase 7: validation
            currentPhase = "validation";
            if (phaseStatusStore != null) {
                phaseStatusStore.startPhase(runId, currentPhase, Instant.now());
            }

            validations.addAll(validationCoordinator.validate(manifest, targetSchema));

            boolean validationFailed = validations.stream().anyMatch(result -> result.status() == ValidationStatus.FAIL);
            if (phaseStatusStore != null) {
                phaseStatusStore.finishPhase(runId, currentPhase, Instant.now(), validationFailed ? "FAILED" : "SUCCESS", (long) validations.size(), "Validation completed");
            }
            currentPhase = null;

            List<ExcludedObjectDecision> excludedDecisions = extractExcludedDecisions(manifest);
            String runStatus = status(validations, viewPlans);
            if (runStatusStore != null) {
                runStatusStore.finishRun(runId, Instant.now(), runStatus);
            }

            reportWriter.write(new MigrationReport(
                runId,
                runStatus,
                started,
                Instant.now(),
                validations,
                errors,
                readinessFindings,
                excludedDecisions,
                chunkStrategies
            ), Path.of(properties.reports().outputDir()));
        } catch (Exception ex) {
            if (currentPhase != null && phaseStatusStore != null) {
                try {
                    phaseStatusStore.finishPhase(runId, currentPhase, Instant.now(), "FAILED", null, ex.getMessage());
                } catch (Exception ignored) {}
            }
            if (runStatusStore != null) {
                runStatusStore.finishRun(runId, Instant.now(), "FAIL");
            }
            ErrorRecord error = errorFor(runId, ex);
            errors.add(error);
            if (auditReady && !(ex instanceof ChunkMigrationException)) {
                saveErrorSafely(error);
            }
            try {
                List<ExcludedObjectDecision> excludedDecisions = extractExcludedDecisions(manifest);
                reportWriter.write(new MigrationReport(
                    runId,
                    "FAIL",
                    started,
                    Instant.now(),
                    validations,
                    errors,
                    readinessFindings,
                    excludedDecisions,
                    chunkStrategies
                ), Path.of(properties.reports().outputDir()));
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
            if (table.status() == ObjectStatus.EXCLUDED) {
                continue;
            }
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

    private List<ExcludedObjectDecision> extractExcludedDecisions(MigrationManifest manifest) {
        if (manifest == null) {
            return List.of();
        }
        List<ExcludedObjectDecision> decisions = new ArrayList<>();
        for (TableMetadata table : manifest.tables()) {
            if (table.status() == ObjectStatus.EXCLUDED || table.exclusionReason() != null) {
                decisions.add(new ExcludedObjectDecision(
                    "TABLE",
                    table.name(),
                    table.status().name(),
                    table.exclusionReason() != null ? table.exclusionReason() : "Excluded by configuration"
                ));
            }
        }
        for (ViewMetadata view : manifest.views()) {
            if (view.status() == ObjectStatus.EXCLUDED || view.exclusionReason() != null) {
                decisions.add(new ExcludedObjectDecision(
                    "VIEW",
                    view.name(),
                    view.status().name(),
                    view.exclusionReason() != null ? view.exclusionReason() : "Excluded by configuration"
                ));
            }
        }
        if (manifest.schemaObjects() != null) {
            for (SchemaObjectMetadata obj : manifest.schemaObjects()) {
                if (obj.status() == ObjectStatus.EXCLUDED || obj.exclusionReason() != null) {
                    decisions.add(new ExcludedObjectDecision(
                        obj.type().name(),
                        obj.name(),
                        obj.status().name(),
                        obj.exclusionReason() != null ? obj.exclusionReason() : "Excluded by configuration"
                    ));
                }
            }
        }
        return decisions;
    }
}

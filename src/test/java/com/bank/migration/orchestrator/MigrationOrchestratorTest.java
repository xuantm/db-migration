package com.bank.migration.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.audit.AuditSchemaService;
import com.bank.migration.manifest.ManifestCacheProperties;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.audit.ErrorLogStore;
import com.bank.migration.chunk.ChunkBoundsService;
import com.bank.migration.chunk.ChunkPlanner;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.load.DataCopyService;
import com.bank.migration.preflight.PreflightCheck;
import com.bank.migration.preflight.PreflightService;
import com.bank.migration.report.MigrationReport;
import com.bank.migration.report.ReportWriter;
import com.bank.migration.scanner.OracleMetadataScanner;
import com.bank.migration.validate.ValidationCoordinator;
import com.bank.migration.view.ViewApplier;
import com.bank.migration.view.ViewPlanner;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MigrationOrchestratorTest {
    @Mock PreflightService preflightService;
    @Mock AuditSchemaService auditSchemaService;
    @Mock OracleMetadataScanner scanner;
    @Mock TargetSchemaService targetSchemaService;
    @Mock TableDdlPlanner ddlPlanner;
    @Mock DdlApplier ddlApplier;
    @Mock ChunkBoundsService chunkBoundsService;
    @Mock ChunkPlanner chunkPlanner;
    @Mock DataCopyService dataCopyService;
    @Mock CheckpointStore checkpointStore;
    @Mock ErrorLogStore errorLogStore;
    @Mock ValidationCoordinator validationCoordinator;
    @Mock ViewPlanner viewPlanner;
    @Mock ViewApplier viewApplier;
    @Mock ReportWriter reportWriter;
    @Mock com.bank.migration.readiness.ReadinessEvaluator readinessEvaluator;

    @Test
    void stopsWhenPreflightFails() {
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports"),
            null,
            null,
            null
        );
        when(preflightService.run("bank_core", true))
            .thenReturn(List.of(new PreflightCheck("target-schema-empty", false, "contains business rows")));

        MigrationOrchestrator orchestrator = orchestrator();

        assertThatThrownBy(() -> orchestrator.run(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Preflight failed: target-schema-empty");

        verify(auditSchemaService, never()).ensureAuditSchema();
        verify(scanner, never()).scan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void stopsWhenReadinessValidationFails() throws Exception {
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports"),
            null,
            null,
            null
        );
        com.bank.migration.domain.MigrationManifest manifest = new com.bank.migration.domain.MigrationManifest(
            "run-123", "BANK_CORE", List.of(), List.of()
        );

        when(preflightService.run("bank_core", true)).thenReturn(List.of(new PreflightCheck("target-schema-empty", true, "ok")));
        when(scanner.scan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("BANK_CORE"))).thenReturn(manifest);

        com.bank.migration.readiness.ReadinessFinding blockingFinding = new com.bank.migration.readiness.ReadinessFinding(
            "RDN-001", com.bank.migration.readiness.ReadinessSeverity.BLOCKER, "COLUMN", "MY_TABLE", "MY_COL",
            "BFILE column is blocker", "Scan", "Exclude", true
        );
        when(readinessEvaluator.evaluate(manifest, props))
            .thenReturn(new com.bank.migration.readiness.ReadinessReport(List.of(blockingFinding), true));

        MigrationOrchestrator orchestrator = orchestrator();

        assertThatThrownBy(() -> orchestrator.run(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Readiness evaluation failed");

        verify(targetSchemaService, never()).prepareCleanSchema(org.mockito.ArgumentMatchers.anyString());

        org.mockito.ArgumentCaptor<MigrationReport> reportCaptor = org.mockito.ArgumentCaptor.forClass(MigrationReport.class);
        verify(reportWriter).write(reportCaptor.capture(), org.mockito.Mockito.any(Path.class));
        assertThat(reportCaptor.getValue().status()).isEqualTo("FAIL");
        assertThat(reportCaptor.getValue().readinessFindings()).containsExactly(blockingFinding);
    }

    @Test
    void stopsOnBfileEvenWithFallbackAllowed() throws Exception {
        com.bank.migration.types.UnsupportedTypePolicy policy = com.bank.migration.types.UnsupportedTypePolicy.FALLBACK_TO_TEXT;
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports"),
            null,
            null,
            null,
            policy
        );

        com.bank.migration.domain.MigrationManifest manifest = new com.bank.migration.domain.MigrationManifest(
            "run-123",
            "BANK_CORE",
            List.of(new com.bank.migration.domain.TableMetadata(
                "BANK_CORE",
                "MY_TABLE",
                com.bank.migration.domain.ObjectStatus.READY,
                List.of(
                    new com.bank.migration.domain.ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                    new com.bank.migration.domain.ColumnMetadata("BFILE_COL", "BFILE", null, null, true, null)
                ),
                List.of(new com.bank.migration.domain.KeyMetadata("PK_MY_TABLE", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of()
            )),
            List.of()
        );

        when(preflightService.run("bank_core", true)).thenReturn(List.of(new PreflightCheck("target-schema-empty", true, "ok")));
        when(scanner.scan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("BANK_CORE"))).thenReturn(manifest);

        com.bank.migration.types.OracleToGaussTypeMapper typeMapper = new com.bank.migration.types.OracleToGaussTypeMapper(policy);
        com.bank.migration.readiness.ReadinessEvaluator realEvaluator = new com.bank.migration.readiness.ReadinessEvaluator(typeMapper);

        MigrationOrchestrator orchestrator = new MigrationOrchestrator(
            preflightService,
            auditSchemaService,
            scanner,
            targetSchemaService,
            ddlPlanner,
            ddlApplier,
            chunkBoundsService,
            chunkPlanner,
            dataCopyService,
            checkpointStore,
            errorLogStore,
            validationCoordinator,
            viewPlanner,
            viewApplier,
            reportWriter,
            realEvaluator
        );

        assertThatThrownBy(() -> orchestrator.run(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Readiness evaluation failed");

        verify(targetSchemaService, never()).prepareCleanSchema(org.mockito.ArgumentMatchers.anyString());
        verify(ddlApplier, never()).apply(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void stopsOnBfileWhenFallbackNotAllowed() throws Exception {
        com.bank.migration.types.UnsupportedTypePolicy policy = com.bank.migration.types.UnsupportedTypePolicy.FAIL;
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports"),
            null,
            null,
            null,
            policy
        );

        com.bank.migration.domain.MigrationManifest manifest = new com.bank.migration.domain.MigrationManifest(
            "run-123",
            "BANK_CORE",
            List.of(new com.bank.migration.domain.TableMetadata(
                "BANK_CORE",
                "MY_TABLE",
                com.bank.migration.domain.ObjectStatus.READY,
                List.of(
                    new com.bank.migration.domain.ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                    new com.bank.migration.domain.ColumnMetadata("BFILE_COL", "BFILE", null, null, true, null)
                ),
                List.of(new com.bank.migration.domain.KeyMetadata("PK_MY_TABLE", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of()
            )),
            List.of()
        );

        when(preflightService.run("bank_core", true)).thenReturn(List.of(new PreflightCheck("target-schema-empty", true, "ok")));
        when(scanner.scan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("BANK_CORE"))).thenReturn(manifest);

        com.bank.migration.types.OracleToGaussTypeMapper typeMapper = new com.bank.migration.types.OracleToGaussTypeMapper(policy);
        com.bank.migration.readiness.ReadinessEvaluator realEvaluator = new com.bank.migration.readiness.ReadinessEvaluator(typeMapper);

        MigrationOrchestrator orchestrator = new MigrationOrchestrator(
            preflightService,
            auditSchemaService,
            scanner,
            targetSchemaService,
            ddlPlanner,
            ddlApplier,
            chunkBoundsService,
            chunkPlanner,
            dataCopyService,
            checkpointStore,
            errorLogStore,
            validationCoordinator,
            viewPlanner,
            viewApplier,
            reportWriter,
            realEvaluator
        );

        assertThatThrownBy(() -> orchestrator.run(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Readiness evaluation failed");

        verify(targetSchemaService, never()).prepareCleanSchema(org.mockito.ArgumentMatchers.anyString());
        verify(ddlApplier, never()).apply(org.mockito.ArgumentMatchers.anyList());
    }

    @Mock com.bank.migration.manifest.ManifestCacheService manifestCacheService;

    @Test
    void loadFromCacheTrueSkipsScannerScanWhenManifestFound() throws Exception {
        ManifestCacheProperties cacheProps = new ManifestCacheProperties(true, true, false, "MY_KEY", false);
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports"),
            null,
            null,
            cacheProps
        );
        com.bank.migration.domain.MigrationManifest cachedManifest = new com.bank.migration.domain.MigrationManifest(
            "run-123", "BANK_CORE", List.of(), List.of()
        );

        when(preflightService.run("bank_core", true)).thenReturn(List.of(new PreflightCheck("target-schema-empty", true, "ok")));
        when(manifestCacheService.load(props)).thenReturn(java.util.Optional.of(cachedManifest));
        when(readinessEvaluator.evaluate(cachedManifest, props))
            .thenReturn(new com.bank.migration.readiness.ReadinessReport(List.of(), false));

        MigrationOrchestrator orchestrator = new MigrationOrchestrator(
            preflightService, auditSchemaService, scanner, targetSchemaService,
            ddlPlanner, ddlApplier, chunkBoundsService, chunkPlanner, dataCopyService,
            checkpointStore, errorLogStore, validationCoordinator, viewPlanner, viewApplier,
            reportWriter, readinessEvaluator, manifestCacheService
        );

        orchestrator.run(props);

        verify(scanner, never()).scan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(manifestCacheService).load(props);
    }

    @Test
    void saveAfterScanPersistsManifestBeforeReadinessEvaluation() throws Exception {
        ManifestCacheProperties cacheProps = new ManifestCacheProperties(true, false, true, "MY_KEY", false);
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports"),
            null,
            null,
            cacheProps
        );
        com.bank.migration.domain.MigrationManifest scannedManifest = new com.bank.migration.domain.MigrationManifest(
            "run-123", "BANK_CORE", List.of(), List.of()
        );

        when(preflightService.run("bank_core", true)).thenReturn(List.of(new PreflightCheck("target-schema-empty", true, "ok")));
        when(manifestCacheService.load(props)).thenReturn(java.util.Optional.empty());
        when(scanner.scan(org.mockito.ArgumentMatchers.anyString(), eq("BANK_CORE"))).thenReturn(scannedManifest);
        when(readinessEvaluator.evaluate(any(), any())).thenReturn(new com.bank.migration.readiness.ReadinessReport(List.of(), false));

        MigrationOrchestrator orchestrator = new MigrationOrchestrator(
            preflightService, auditSchemaService, scanner, targetSchemaService,
            ddlPlanner, ddlApplier, chunkBoundsService, chunkPlanner, dataCopyService,
            checkpointStore, errorLogStore, validationCoordinator, viewPlanner, viewApplier,
            reportWriter, readinessEvaluator, manifestCacheService
        );

        orchestrator.run(props);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(manifestCacheService, scanner, readinessEvaluator);
        inOrder.verify(manifestCacheService).load(props);
        inOrder.verify(scanner).scan(org.mockito.ArgumentMatchers.anyString(), eq("BANK_CORE"));
        inOrder.verify(manifestCacheService).save(props, scannedManifest);
        inOrder.verify(readinessEvaluator).evaluate(any(), eq(props));
    }

    private MigrationOrchestrator orchestrator() {
        return new MigrationOrchestrator(
            preflightService,
            auditSchemaService,
            scanner,
            targetSchemaService,
            ddlPlanner,
            ddlApplier,
            chunkBoundsService,
            chunkPlanner,
            dataCopyService,
            checkpointStore,
            errorLogStore,
            validationCoordinator,
            viewPlanner,
            viewApplier,
            reportWriter,
            readinessEvaluator
        );
    }
}

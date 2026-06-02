package com.bank.migration.orchestrator;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.audit.AuditSchemaService;
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
import com.bank.migration.report.ReportWriter;
import com.bank.migration.scanner.OracleMetadataScanner;
import com.bank.migration.validate.ValidationCoordinator;
import com.bank.migration.view.ViewApplier;
import com.bank.migration.view.ViewPlanner;
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

    @Test
    void stopsWhenPreflightFails() {
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports")
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
            reportWriter
        );
    }
}

package com.bank.migration.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.audit.AuditSchemaService;
import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.audit.ChunkStatus;
import com.bank.migration.audit.ErrorLogStore;
import com.bank.migration.chunk.ChunkBounds;
import com.bank.migration.chunk.ChunkBoundsService;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.chunk.ChunkPlanner;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.DdlStatement;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.domain.ViewMetadata;
import com.bank.migration.load.ChunkMigrationException;
import com.bank.migration.load.DataCopyService;
import com.bank.migration.load.TableCopyResult;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class MigrationOrchestratorPipelineTest {
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
    void runsCleanLoadPipelineInOrder() throws Exception {
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports")
        );
        TableMetadata account = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(new ColumnMetadata("ID", "NUMBER", 19, 0, false, null)),
            List.of(),
            List.of()
        );
        ViewMetadata view = new ViewMetadata("BANK_CORE", "VW_ACCOUNT", ObjectStatus.READY, "select ID from ACCOUNT", List.of(), List.of());
        MigrationManifest manifest = new MigrationManifest("run-001", "BANK_CORE", List.of(account), List.of(view));
        ChunkPlan chunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 10", "PRIMARY_KEY_RANGE");
        DdlStatement tableDdl = new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)");
        DdlStatement constraintDdl = new DdlStatement("CONSTRAINT", "PK_ACCOUNT", "alter table bank_core.account add primary key (id)");
        ViewPlan viewPlan = new ViewPlan("VW_ACCOUNT", ObjectStatus.READY, "create or replace view bank_core.vw_account as select ID from ACCOUNT", List.of());
        List<ValidationResult> validations = List.of(new ValidationResult("row-count", ValidationStatus.PASS, "ACCOUNT", "source=10 target=10"));

        when(preflightService.run("bank_core", true)).thenReturn(List.of(new PreflightCheck("target-schema-empty", true, "ok")));
        when(scanner.scan(org.mockito.ArgumentMatchers.anyString(), eq("BANK_CORE"))).thenReturn(manifest);
        when(ddlPlanner.plan("bank_core", account)).thenReturn(List.of(tableDdl, constraintDdl));
        when(chunkBoundsService.bounds(account)).thenReturn(new ChunkBounds(1L, 10L));
        when(chunkPlanner.plan(account, 1L, 10L, 5000)).thenReturn(List.of(chunk));
        when(dataCopyService.copyChunk(account, "bank_core", chunk)).thenReturn(new TableCopyResult(10, 10));
        when(viewPlanner.plan("bank_core", view)).thenReturn(viewPlan);
        when(validationCoordinator.validate(manifest, "bank_core")).thenReturn(validations);

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
            reportWriter
        );

        orchestrator.run(props);

        InOrder order = inOrder(auditSchemaService, targetSchemaService, ddlApplier, dataCopyService, viewApplier, validationCoordinator, reportWriter);
        order.verify(auditSchemaService).ensureAuditSchema();
        order.verify(targetSchemaService).prepareCleanSchema("bank_core");
        order.verify(ddlApplier).apply(List.of(tableDdl));
        order.verify(dataCopyService).copyChunk(account, "bank_core", chunk);
        order.verify(ddlApplier).apply(List.of(constraintDdl));
        order.verify(viewApplier).applyReadyViews("bank_core", List.of(viewPlan));
        order.verify(validationCoordinator).validate(manifest, "bank_core");
        order.verify(reportWriter).write(any(MigrationReport.class), eq(Path.of("build/reports")));
        verify(checkpointStore).save(org.mockito.ArgumentMatchers.argThat(record ->
            record.status() == ChunkStatus.SUCCESS
                && record.tableName().equals("ACCOUNT")
                && record.chunkId().equals("ACCOUNT-000001")
                && record.rowsRead() == 10L
                && record.rowsWritten() == 10L
        ));
    }

    @Test
    void writesChunkLevelErrorToReportWhenDataLoadFails() throws Exception {
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports")
        );
        TableMetadata account = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(new ColumnMetadata("ID", "NUMBER", 19, 0, false, null)),
            List.of(),
            List.of()
        );
        MigrationManifest manifest = new MigrationManifest("run-001", "BANK_CORE", List.of(account), List.of());
        ChunkPlan chunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 10", "PRIMARY_KEY_RANGE");
        DdlStatement tableDdl = new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)");
        when(preflightService.run("bank_core", true)).thenReturn(List.of(new PreflightCheck("target-schema-empty", true, "ok")));
        when(scanner.scan(org.mockito.ArgumentMatchers.anyString(), eq("BANK_CORE"))).thenReturn(manifest);
        when(ddlPlanner.plan("bank_core", account)).thenReturn(List.of(tableDdl));
        when(chunkBoundsService.bounds(account)).thenReturn(new ChunkBounds(1L, 10L));
        when(chunkPlanner.plan(account, 1L, 10L, 5000)).thenReturn(List.of(chunk));
        when(dataCopyService.copyChunk(account, "bank_core", chunk))
            .thenThrow(new DataAccessResourceFailureException("source read failed"));
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
            reportWriter
        );

        assertThatThrownBy(() -> orchestrator.run(props))
            .isInstanceOf(ChunkMigrationException.class)
            .hasMessageContaining("source read failed");

        ArgumentCaptor<MigrationReport> reportCaptor = ArgumentCaptor.forClass(MigrationReport.class);
        verify(reportWriter).write(reportCaptor.capture(), eq(Path.of("build/reports")));
        assertThat(reportCaptor.getValue().status()).isEqualTo("FAIL");
        assertThat(reportCaptor.getValue().errors()).anySatisfy(error -> {
            assertThat(error.phase()).isEqualTo("data-load");
            assertThat(error.objectName()).isEqualTo("ACCOUNT");
            assertThat(error.chunkId()).isEqualTo("ACCOUNT-000001");
            assertThat(error.message()).contains("source read failed");
        });
    }
}

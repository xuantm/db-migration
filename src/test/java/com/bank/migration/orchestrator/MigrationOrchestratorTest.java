package com.bank.migration.orchestrator;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.DdlStatement;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.scanner.OracleMetadataScanner;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MigrationOrchestratorTest {
    @Mock OracleMetadataScanner scanner;
    @Mock TargetSchemaService targetSchemaService;
    @Mock TableDdlPlanner ddlPlanner;
    @Mock DdlApplier ddlApplier;

    @Test
    void scansPreparesAndAppliesTableDdl() {
        MigrationProperties props = new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports")
        );
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        when(scanner.scan(anyString(), eq("BANK_CORE")))
            .thenReturn(new MigrationManifest("run-001", "BANK_CORE", List.of(table), List.of()));
        when(ddlPlanner.plan("bank_core", table))
            .thenReturn(List.of(new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)")));

        MigrationOrchestrator orchestrator = new MigrationOrchestrator(scanner, targetSchemaService, ddlPlanner, ddlApplier);

        orchestrator.run(props);

        verify(targetSchemaService).prepareCleanSchema("bank_core");
        verify(ddlApplier).apply(List.of(new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)")));
    }
}

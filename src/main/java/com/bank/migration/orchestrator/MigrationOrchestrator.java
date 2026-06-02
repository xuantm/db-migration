package com.bank.migration.orchestrator;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.ddl.DdlApplier;
import com.bank.migration.ddl.DdlStatement;
import com.bank.migration.ddl.TableDdlPlanner;
import com.bank.migration.ddl.TargetSchemaService;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.scanner.OracleMetadataScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MigrationOrchestrator {
    private final OracleMetadataScanner scanner;
    private final TargetSchemaService targetSchemaService;
    private final TableDdlPlanner ddlPlanner;
    private final DdlApplier ddlApplier;

    public MigrationOrchestrator(
        OracleMetadataScanner scanner,
        TargetSchemaService targetSchemaService,
        TableDdlPlanner ddlPlanner,
        DdlApplier ddlApplier
    ) {
        this.scanner = scanner;
        this.targetSchemaService = targetSchemaService;
        this.ddlPlanner = ddlPlanner;
        this.ddlApplier = ddlApplier;
    }

    public void run(MigrationProperties properties) {
        String runId = "run-" + UUID.randomUUID();
        MigrationManifest manifest = scanner.scan(runId, properties.source().schema());
        if (properties.cleanLoad()) {
            targetSchemaService.prepareCleanSchema(properties.target().schema());
        }

        List<DdlStatement> statements = new ArrayList<>();
        for (TableMetadata table : manifest.tables()) {
            statements.addAll(ddlPlanner.plan(properties.target().schema(), table));
        }
        ddlApplier.apply(statements);
    }
}

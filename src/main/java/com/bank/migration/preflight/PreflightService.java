package com.bank.migration.preflight;

import com.bank.migration.config.MigrationMode;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PreflightService {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;

    public PreflightService(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
    }

    public List<PreflightCheck> run(String targetSchema, boolean cleanLoad) {
        return run(targetSchema, cleanLoad, MigrationMode.FULL);
    }

    public List<PreflightCheck> run(String targetSchema, boolean cleanLoad, MigrationMode mode) {
        List<PreflightCheck> checks = new ArrayList<>();
        checks.add(safeCheck("source-connectivity", this::checkSourceConnectivity));
        checks.add(safeCheck("target-connectivity", this::checkTargetConnectivity));
        if (mode == MigrationMode.FULL) {
            checks.add(safeCheck("target-schema-empty", () -> checkTargetSchemaEmpty(targetSchema, cleanLoad)));
        }
        return checks;
    }

    private PreflightCheck safeCheck(String name, Supplier<PreflightCheck> check) {
        try {
            return check.get();
        } catch (DataAccessException ex) {
            return new PreflightCheck(name, false, name + " failed: " + ex.getMessage());
        }
    }

    private PreflightCheck checkSourceConnectivity() {
        Integer value = sourceJdbc.queryForObject("select 1 from dual", Integer.class);
        return new PreflightCheck(
            "source-connectivity",
            value != null && value == 1,
            "Oracle source connectivity check completed"
        );
    }

    private PreflightCheck checkTargetConnectivity() {
        Integer value = targetJdbc.queryForObject("select 1", Integer.class);
        return new PreflightCheck(
            "target-connectivity",
            value != null && value == 1,
            "GaussDB target connectivity check completed"
        );
    }

    private PreflightCheck checkTargetSchemaEmpty(String targetSchema, boolean cleanLoad) {
        String normalizedSchema = targetRenderer.physicalName(targetSchema);
        Integer tableCount = targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            normalizedSchema
        );
        Integer objectCount = targetJdbc.queryForObject(
            "select count(*) from pg_catalog.pg_class c " +
                "join pg_catalog.pg_namespace n on n.oid = c.relnamespace " +
                "where n.nspname = ? and c.relkind in ('r','p','v','m','S','f')",
            Integer.class,
            normalizedSchema
        );
        boolean empty = tableCount == null || tableCount == 0;
        boolean hasObjects = objectCount != null && objectCount > 0;
        boolean passed = !cleanLoad || (empty && !hasObjects);
        String message = empty && !hasObjects
            ? "Target schema is empty"
            : "Target schema contains " + safeCount(tableCount) + " existing tables and "
                + safeCount(objectCount) + " existing objects";
        return new PreflightCheck("target-schema-empty", passed, message);
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }
}

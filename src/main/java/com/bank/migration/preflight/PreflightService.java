package com.bank.migration.preflight;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PreflightService {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;

    public PreflightService(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
    }

    public List<PreflightCheck> run(String targetSchema, boolean cleanLoad) {
        List<PreflightCheck> checks = new ArrayList<>();
        checks.add(checkSourceConnectivity());
        checks.add(checkTargetConnectivity());
        checks.add(checkTargetSchemaEmpty(targetSchema, cleanLoad));
        return checks;
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
        Integer tableCount = targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            targetSchema.toLowerCase()
        );
        boolean empty = tableCount == null || tableCount == 0;
        boolean passed = empty || !cleanLoad;
        String message = empty
            ? "Target schema is empty"
            : "Target schema contains " + tableCount + " existing tables";
        return new PreflightCheck("target-schema-empty", passed, message);
    }
}

package com.bank.migration.audit;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RunStatusStore {
    private final JdbcTemplate jdbc;

    public RunStatusStore(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void startRun(String runId, String sourceSchema, String targetSchema, Instant startedAt) {
        jdbc.update(
            "insert into migration_audit.runs (run_id, source_schema, target_schema, started_at) values (?, ?, ?, ?)",
            runId, sourceSchema, targetSchema, Timestamp.from(startedAt)
        );
    }

    public void finishRun(String runId, Instant finishedAt, String status) {
        jdbc.update(
            "update migration_audit.runs set finished_at = ?, final_status = ? where run_id = ?",
            Timestamp.from(finishedAt), status, runId
        );
    }
}

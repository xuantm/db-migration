package com.bank.migration.audit;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PhaseStatusStore {
    private final JdbcTemplate jdbc;

    public PhaseStatusStore(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void startPhase(String runId, String phaseName, Instant startedAt) {
        jdbc.update(
            "insert into migration_audit.phase_status (run_id, phase_name, status, started_at) values (?, ?, ?, ?)",
            runId, phaseName, "RUNNING", Timestamp.from(startedAt)
        );
    }

    public void finishPhase(String runId, String phaseName, Instant finishedAt, String status, Long rowsProcessed, String message) {
        jdbc.update(
            "update migration_audit.phase_status set finished_at = ?, status = ?, rows_processed = ?, message = ? where run_id = ? and phase_name = ?",
            Timestamp.from(finishedAt), status, rowsProcessed, message, runId, phaseName
        );
    }
}

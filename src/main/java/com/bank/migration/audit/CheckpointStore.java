package com.bank.migration.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CheckpointStore {
    public static final String UPDATE_SQL = """
        update migration_audit.checkpoints
        set rows_read = ?,
            rows_written = ?,
            status = ?,
            retry_count = ?,
            completed_at = ?,
            last_error_id = ?
        where run_id = ? and table_name = ? and chunk_id = ?
        """;

    public static final String INSERT_SQL = """
        insert into migration_audit.checkpoints
        (run_id, schema_name, table_name, chunk_id, chunk_range, rows_read, rows_written, status, retry_count, started_at, completed_at, last_error_id)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private final JdbcTemplate jdbc;

    public CheckpointStore(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(CheckpointRecord record) {
        int updated = jdbc.update(
            UPDATE_SQL,
            record.rowsRead(),
            record.rowsWritten(),
            record.status().name(),
            record.retryCount(),
            record.completedAt() != null ? java.sql.Timestamp.from(record.completedAt()) : null,
            record.lastErrorId(),
            record.runId(),
            record.tableName(),
            record.chunkId()
        );
        if (updated == 0) {
            jdbc.update(
                INSERT_SQL,
                record.runId(),
                record.schemaName(),
                record.tableName(),
                record.chunkId(),
                record.chunkRange(),
                record.rowsRead(),
                record.rowsWritten(),
                record.status().name(),
                record.retryCount(),
                record.startedAt() != null ? java.sql.Timestamp.from(record.startedAt()) : null,
                record.completedAt() != null ? java.sql.Timestamp.from(record.completedAt()) : null,
                record.lastErrorId()
            );
        }
    }
}

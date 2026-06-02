package com.bank.migration.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CheckpointStore {
    public static final String UPSERT_SQL = """
        insert into migration_audit.checkpoints
        (run_id, schema_name, table_name, chunk_id, chunk_range, rows_read, rows_written, status, retry_count, started_at, completed_at, last_error_id)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        on conflict (run_id, table_name, chunk_id)
        do update set rows_read = excluded.rows_read,
                      rows_written = excluded.rows_written,
                      status = excluded.status,
                      retry_count = excluded.retry_count,
                      completed_at = excluded.completed_at,
                      last_error_id = excluded.last_error_id
        """;

    private final JdbcTemplate jdbc;

    public CheckpointStore(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(CheckpointRecord record) {
        jdbc.update(
            UPSERT_SQL,
            record.runId(),
            record.schemaName(),
            record.tableName(),
            record.chunkId(),
            record.chunkRange(),
            record.rowsRead(),
            record.rowsWritten(),
            record.status().name(),
            record.retryCount(),
            record.startedAt(),
            record.completedAt(),
            record.lastErrorId()
        );
    }
}

package com.bank.migration.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ErrorLogStore {
    private static final String INSERT_SQL = """
        insert into migration_audit.errors
        (error_id, run_id, phase, object_type, object_name, chunk_id, sql_text, database_code, message, action_category, created_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    private final JdbcTemplate jdbc;

    public ErrorLogStore(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ErrorRecord record) {
        jdbc.update(
            INSERT_SQL,
            record.errorId(),
            record.runId(),
            record.phase(),
            record.objectType(),
            record.objectName(),
            record.chunkId(),
            record.sqlText(),
            record.databaseCode(),
            record.message(),
            record.actionCategory(),
            record.createdAt()
        );
    }
}

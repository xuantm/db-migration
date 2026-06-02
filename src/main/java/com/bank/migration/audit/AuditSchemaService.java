package com.bank.migration.audit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditSchemaService {
    private final JdbcTemplate jdbc;

    public AuditSchemaService(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ensureAuditSchema() {
        jdbc.execute("create schema if not exists migration_audit");
        jdbc.execute("""
            create table if not exists migration_audit.checkpoints (
              run_id varchar(80) not null,
              schema_name varchar(128) not null,
              table_name varchar(128) not null,
              chunk_id varchar(160) not null,
              chunk_range text not null,
              rows_read bigint not null,
              rows_written bigint not null,
              status varchar(30) not null,
              retry_count integer not null,
              started_at timestamp,
              completed_at timestamp,
              last_error_id varchar(120),
              primary key (run_id, table_name, chunk_id)
            )
            """);
        jdbc.execute("""
            create table if not exists migration_audit.errors (
              error_id varchar(120) primary key,
              run_id varchar(80) not null,
              phase varchar(40) not null,
              object_type varchar(40) not null,
              object_name varchar(256),
              chunk_id varchar(160),
              sql_text text,
              database_code varchar(120),
              message text not null,
              action_category varchar(80) not null,
              created_at timestamp not null
            )
            """);
    }
}

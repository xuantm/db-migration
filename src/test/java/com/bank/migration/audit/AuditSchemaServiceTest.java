package com.bank.migration.audit;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AuditSchemaServiceTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void createsAuditTables() {
        AuditSchemaService service = new AuditSchemaService(jdbc);

        service.ensureAuditSchema();

        verify(jdbc).execute("create schema if not exists migration_audit");
    }
}

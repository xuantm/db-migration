package com.bank.migration.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("migration_audit.checkpoints"));
        verify(jdbc).execute(org.mockito.ArgumentMatchers.contains("migration_audit.errors"));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.atLeast(1)).execute(sqlCaptor.capture());
        List<String> executedSql = sqlCaptor.getAllValues();
        String manifestCacheDdl = executedSql.stream()
            .filter(sql -> sql.contains("migration_audit.manifest_cache"))
            .findFirst()
            .orElseThrow();
        assertThat(manifestCacheDdl).contains("manifest_version");
        assertThat(manifestCacheDdl).contains("tool_version");
        assertThat(manifestCacheDdl).contains("scanner_version");
        assertThat(manifestCacheDdl).contains("config_hash");
        assertThat(manifestCacheDdl).contains("manifest_checksum");
        assertThat(manifestCacheDdl).contains("manifest_json");
    }
}

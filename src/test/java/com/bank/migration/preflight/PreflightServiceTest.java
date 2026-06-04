package com.bank.migration.preflight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.startsWith;

import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.config.MigrationMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PreflightServiceTest {
    @Mock JdbcTemplate sourceJdbc;
    @Mock JdbcTemplate targetJdbc;

    private PreflightService serviceWithPolicy(IdentifierMappingPolicy policy) {
        return new PreflightService(sourceJdbc, targetJdbc, new IdentifierRenderer(new GaussDialect(policy)));
    }

    @Test
    void failsWhenTargetSchemaContainsRows() {
        when(sourceJdbc.queryForObject("select 1 from dual", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            "bank_core"
        )).thenReturn(3);
        when(targetJdbc.queryForObject(
            "select count(*) from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace where n.nspname = ? and c.relkind in ('r','p','v','m','S','f')",
            Integer.class,
            "bank_core"
        )).thenReturn(3);

        PreflightService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        List<PreflightCheck> checks = service.run("bank_core", true);

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("target-schema-empty");
            assertThat(check.passed()).isFalse();
            assertThat(check.message()).contains("contains 3 existing tables");
        });
    }

    @Test
    void sourceConnectivityFailureStillRunsTargetChecks() {
        when(sourceJdbc.queryForObject("select 1 from dual", Integer.class))
            .thenThrow(new DataAccessResourceFailureException("source unavailable"));
        when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            "bank_core"
        )).thenReturn(0);
        when(targetJdbc.queryForObject(
            "select count(*) from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace where n.nspname = ? and c.relkind in ('r','p','v','m','S','f')",
            Integer.class,
            "bank_core"
        )).thenReturn(0);

        PreflightService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        List<PreflightCheck> checks = service.run("bank_core", true);

        assertThat(checks).hasSize(3);
        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("source-connectivity");
            assertThat(check.passed()).isFalse();
            assertThat(check.message()).contains("source unavailable");
        });
        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("target-connectivity");
            assertThat(check.passed()).isTrue();
        });
        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("target-schema-empty");
            assertThat(check.passed()).isTrue();
        });
    }

    @Test
    void cleanLoadFalseAllowsExistingObjectsButReportsThem() {
        when(sourceJdbc.queryForObject("select 1 from dual", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            "bank_core"
        )).thenReturn(2);
        when(targetJdbc.queryForObject(
            "select count(*) from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace where n.nspname = ? and c.relkind in ('r','p','v','m','S','f')",
            Integer.class,
            "bank_core"
        )).thenReturn(5);

        PreflightService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        List<PreflightCheck> checks = service.run("bank_core", false);

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("target-schema-empty");
            assertThat(check.passed()).isTrue();
            assertThat(check.message()).contains("2 existing tables");
            assertThat(check.message()).contains("5 existing objects");
        });
    }

    @Test
    void nonTableObjectsFailCleanLoadEvenWhenTableCountIsZero() {
        when(sourceJdbc.queryForObject("select 1 from dual", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            "bank_core"
        )).thenReturn(0);
        when(targetJdbc.queryForObject(
            "select count(*) from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace where n.nspname = ? and c.relkind in ('r','p','v','m','S','f')",
            Integer.class,
            "bank_core"
        )).thenReturn(2);

        PreflightService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        List<PreflightCheck> checks = service.run("bank_core", true);

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("target-schema-empty");
            assertThat(check.passed()).isFalse();
            assertThat(check.message()).contains("0 existing tables");
            assertThat(check.message()).contains("2 existing objects");
        });
    }

    @Test
    void metadataQueriesCompareAgainstUserUnderQuotePolicy() {
        when(sourceJdbc.queryForObject("select 1 from dual", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            "USER"
        )).thenReturn(0);
        when(targetJdbc.queryForObject(
            "select count(*) from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace where n.nspname = ? and c.relkind in ('r','p','v','m','S','f')",
            Integer.class,
            "USER"
        )).thenReturn(0);

        PreflightService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        List<PreflightCheck> checks = service.run("USER", true);

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("target-schema-empty");
            assertThat(check.passed()).isTrue();
        });
    }

    @Test
    void metadataQueriesCompareAgainstUserUnderRenamePolicy() {
        when(sourceJdbc.queryForObject("select 1 from dual", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            "user_"
        )).thenReturn(0);
        when(targetJdbc.queryForObject(
            "select count(*) from pg_catalog.pg_class c join pg_catalog.pg_namespace n on n.oid = c.relnamespace where n.nspname = ? and c.relkind in ('r','p','v','m','S','f')",
            Integer.class,
            "user_"
        )).thenReturn(0);

        PreflightService service = serviceWithPolicy(IdentifierMappingPolicy.RENAME);

        List<PreflightCheck> checks = service.run("USER", true);

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("target-schema-empty");
            assertThat(check.passed()).isTrue();
        });
    }

    @Test
    void dataOnlyPreflightSkipsTargetSchemaEmptyCheck() {
        when(sourceJdbc.queryForObject("select 1 from dual", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);

        PreflightService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        List<PreflightCheck> checks = service.run("bank_core", true, MigrationMode.DATA_ONLY);

        assertThat(checks).extracting(PreflightCheck::name)
            .containsExactly("source-connectivity", "target-connectivity");
        verify(targetJdbc, never()).queryForObject(
            startsWith("select count(*) from information_schema.tables"),
            eq(Integer.class),
            any()
        );
    }
}

package com.bank.migration.preflight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PreflightServiceTest {
    @Mock JdbcTemplate sourceJdbc;
    @Mock JdbcTemplate targetJdbc;

    @Test
    void failsWhenTargetSchemaContainsRows() {
        when(sourceJdbc.queryForObject("select 1 from dual", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
        when(targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ?",
            Integer.class,
            "bank_core"
        )).thenReturn(3);

        PreflightService service = new PreflightService(sourceJdbc, targetJdbc);

        List<PreflightCheck> checks = service.run("bank_core", true);

        assertThat(checks).anySatisfy(check -> {
            assertThat(check.name()).isEqualTo("target-schema-empty");
            assertThat(check.passed()).isFalse();
            assertThat(check.message()).contains("contains 3 existing tables");
        });
    }
}

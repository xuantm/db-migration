package com.bank.migration.ddl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TargetSchemaServiceTest {
    @Mock JdbcTemplate targetJdbc;

    @Test
    void dropsAndRecreatesSchemaForCleanLoad() {
        TargetSchemaService service = new TargetSchemaService(targetJdbc);

        service.prepareCleanSchema("BANK_CORE");

        verify(targetJdbc).execute("drop schema if exists bank_core cascade");
        verify(targetJdbc).execute("create schema bank_core");
    }

    @Test
    void rejectsBlankSchemaName() {
        TargetSchemaService service = new TargetSchemaService(targetJdbc);

        assertThatThrownBy(() -> service.prepareCleanSchema(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Target schema");
    }

    @Test
    void rejectsUnsafeSchemaName() {
        TargetSchemaService service = new TargetSchemaService(targetJdbc);

        assertThatThrownBy(() -> service.prepareCleanSchema("bank_core; drop schema audit"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("simple unquoted identifier");
    }
}

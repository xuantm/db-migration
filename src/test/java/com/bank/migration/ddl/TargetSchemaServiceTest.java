package com.bank.migration.ddl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TargetSchemaServiceTest {
    @Mock JdbcTemplate targetJdbc;

    private TargetSchemaService serviceWithPolicy(IdentifierMappingPolicy policy) {
        return new TargetSchemaService(targetJdbc, new IdentifierRenderer(new GaussDialect(policy)));
    }

    @Test
    void dropsAndRecreatesSchemaForCleanLoad() {
        TargetSchemaService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        service.prepareCleanSchema("BANK_CORE");

        verify(targetJdbc).execute("drop schema if exists bank_core cascade");
        verify(targetJdbc).execute("create schema bank_core");
    }

    @Test
    void prepareCleanSchemaUsesUserUnderQuotePolicy() {
        TargetSchemaService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        service.prepareCleanSchema("USER");

        verify(targetJdbc).execute("drop schema if exists \"USER\" cascade");
        verify(targetJdbc).execute("create schema \"USER\"");
    }

    @Test
    void prepareCleanSchemaUsesUserUnderRenamePolicy() {
        TargetSchemaService service = serviceWithPolicy(IdentifierMappingPolicy.RENAME);

        service.prepareCleanSchema("USER");

        verify(targetJdbc).execute("drop schema if exists user_ cascade");
        verify(targetJdbc).execute("create schema user_");
    }

    @Test
    void rejectsBlankSchemaName() {
        TargetSchemaService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        assertThatThrownBy(() -> service.prepareCleanSchema(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Target schema");
    }

    @Test
    void rejectsUnsafeSchemaName() {
        TargetSchemaService service = serviceWithPolicy(IdentifierMappingPolicy.QUOTE);

        assertThatThrownBy(() -> service.prepareCleanSchema("bank_core; drop schema audit"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("simple unquoted identifier");
    }
}

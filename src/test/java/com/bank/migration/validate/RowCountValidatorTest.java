package com.bank.migration.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.dialect.OracleDialect;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RowCountValidatorTest {
    @Mock JdbcTemplate sourceJdbc;
    @Mock JdbcTemplate targetJdbc;

    private final IdentifierRenderer sourceRenderer = new IdentifierRenderer(new OracleDialect());
    private final IdentifierRenderer targetRenderer = new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.QUOTE));

    @Test
    void failsWhenCountsDiffer() {
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        when(sourceJdbc.queryForObject("select count(*) from BANK_CORE.ACCOUNT", Long.class)).thenReturn(100L);
        when(targetJdbc.queryForObject("select count(*) from bank_core.account", Long.class)).thenReturn(99L);

        RowCountValidator validator = new RowCountValidator(sourceJdbc, targetJdbc, sourceRenderer, targetRenderer);

        ValidationResult result = validator.validate(table, "bank_core");

        assertThat(result.status()).isEqualTo(ValidationStatus.FAIL);
        assertThat(result.message()).contains("source=100 target=99");
    }
}

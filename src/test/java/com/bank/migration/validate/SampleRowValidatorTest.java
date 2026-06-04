package com.bank.migration.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.dialect.OracleDialect;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

@ExtendWith(MockitoExtension.class)
class SampleRowValidatorTest {
    @Mock JdbcTemplate sourceJdbc;
    @Mock JdbcTemplate targetJdbc;

    private final IdentifierRenderer sourceRenderer = new IdentifierRenderer(new OracleDialect());
    private final IdentifierRenderer targetRenderer = new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.QUOTE));

    @Test
    void passesWhenRowsMatch() {
        ColumnMetadata col = new ColumnMetadata("ID", "NUMBER", 19, 0, false, null);
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(col), List.of(), List.of());

        // Mock source query execution
        when(sourceJdbc.query(eq("select ID from BANK_CORE.ACCOUNT order by ID"), any(ResultSetExtractor.class)))
            .thenAnswer(invocation -> {
                ResultSetExtractor<List<List<Object>>> extractor = invocation.getArgument(1);
                ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                when(rs.next()).thenReturn(true, false);
                when(rs.getObject(1)).thenReturn(1L);
                return extractor.extractData(rs);
            });

        // Mock target query execution
        when(targetJdbc.query(eq("select id from bank_core.account order by id"), any(ResultSetExtractor.class)))
            .thenAnswer(invocation -> {
                ResultSetExtractor<List<List<Object>>> extractor = invocation.getArgument(1);
                ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                when(rs.next()).thenReturn(true, false);
                when(rs.getObject(1)).thenReturn(1L);
                return extractor.extractData(rs);
            });

        SampleRowValidator validator = new SampleRowValidator(sourceJdbc, targetJdbc, sourceRenderer, targetRenderer);
        ValidationResult result = validator.validate(table, "bank_core");

        assertThat(result.status()).isEqualTo(ValidationStatus.PASS);
    }

    @Test
    void failsWhenRowsMismatch() {
        ColumnMetadata col = new ColumnMetadata("ID", "NUMBER", 19, 0, false, null);
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(col), List.of(), List.of());

        // Mock source query execution
        when(sourceJdbc.query(eq("select ID from BANK_CORE.ACCOUNT order by ID"), any(ResultSetExtractor.class)))
            .thenAnswer(invocation -> {
                ResultSetExtractor<List<List<Object>>> extractor = invocation.getArgument(1);
                ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                when(rs.next()).thenReturn(true, false);
                when(rs.getObject(1)).thenReturn(1L);
                return extractor.extractData(rs);
            });

        // Mock target query execution
        when(targetJdbc.query(eq("select id from bank_core.account order by id"), any(ResultSetExtractor.class)))
            .thenAnswer(invocation -> {
                ResultSetExtractor<List<List<Object>>> extractor = invocation.getArgument(1);
                ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
                when(rs.next()).thenReturn(true, false);
                when(rs.getObject(1)).thenReturn(2L);
                return extractor.extractData(rs);
            });

        SampleRowValidator validator = new SampleRowValidator(sourceJdbc, targetJdbc, sourceRenderer, targetRenderer);
        ValidationResult result = validator.validate(table, "bank_core");

        assertThat(result.status()).isEqualTo(ValidationStatus.FAIL);
    }
}

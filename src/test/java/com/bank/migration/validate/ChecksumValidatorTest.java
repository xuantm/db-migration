package com.bank.migration.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.dialect.OracleDialect;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

@ExtendWith(MockitoExtension.class)
class ChecksumValidatorTest {
    @Mock JdbcTemplate sourceJdbc;
    @Mock JdbcTemplate targetJdbc;

    private final IdentifierRenderer sourceRenderer = new IdentifierRenderer(new OracleDialect());
    private final IdentifierRenderer targetRenderer = new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.QUOTE));

    @Test
    void passesWhenChecksumsMatch() {
        ColumnMetadata col1 = new ColumnMetadata("ID", "NUMBER", 19, 0, false, null);
        ColumnMetadata col2 = new ColumnMetadata("NAME", "VARCHAR2", 100, null, true, null);
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(col1, col2), List.of(), List.of());

        // Mock source query execution
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            when(rs.getObject(1)).thenReturn(1L);
            when(rs.getObject(2)).thenReturn("Account 1");
            handler.processRow(rs);
            return null;
        }).when(sourceJdbc).query(eq("select ID, NAME from BANK_CORE.ACCOUNT"), any(RowCallbackHandler.class));

        // Mock target query execution (same values)
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            when(rs.getObject(1)).thenReturn(1L);
            when(rs.getObject(2)).thenReturn("Account 1");
            handler.processRow(rs);
            return null;
        }).when(targetJdbc).query(eq("select id, name from bank_core.account"), any(RowCallbackHandler.class));

        ChecksumValidator validator = new ChecksumValidator(sourceJdbc, targetJdbc, sourceRenderer, targetRenderer);
        ValidationResult result = validator.validate(table, "bank_core");

        assertThat(result.status()).isEqualTo(ValidationStatus.PASS);
    }

    @Test
    void failsWhenChecksumsMismatch() {
        ColumnMetadata col1 = new ColumnMetadata("ID", "NUMBER", 19, 0, false, null);
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(col1), List.of(), List.of());

        // Mock source query execution
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            when(rs.getObject(1)).thenReturn(1L);
            handler.processRow(rs);
            return null;
        }).when(sourceJdbc).query(eq("select ID from BANK_CORE.ACCOUNT"), any(RowCallbackHandler.class));

        // Mock target query execution (different value)
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            when(rs.getObject(1)).thenReturn(2L);
            handler.processRow(rs);
            return null;
        }).when(targetJdbc).query(eq("select id from bank_core.account"), any(RowCallbackHandler.class));

        ChecksumValidator validator = new ChecksumValidator(sourceJdbc, targetJdbc, sourceRenderer, targetRenderer);
        ValidationResult result = validator.validate(table, "bank_core");

        assertThat(result.status()).isEqualTo(ValidationStatus.FAIL);
    }

    @Test
    void skipsLobColumnsAndWarns() {
        ColumnMetadata col1 = new ColumnMetadata("ID", "NUMBER", 19, 0, false, null);
        ColumnMetadata col2 = new ColumnMetadata("DOC", "BLOB", null, null, true, null);
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(col1, col2), List.of(), List.of());

        // Mock source query execution (BLOB is excluded, only ID is queried)
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            when(rs.getObject(1)).thenReturn(1L);
            handler.processRow(rs);
            return null;
        }).when(sourceJdbc).query(eq("select ID from BANK_CORE.ACCOUNT"), any(RowCallbackHandler.class));

        // Mock target query execution
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            when(rs.getObject(1)).thenReturn(1L);
            handler.processRow(rs);
            return null;
        }).when(targetJdbc).query(eq("select id from bank_core.account"), any(RowCallbackHandler.class));

        ChecksumValidator validator = new ChecksumValidator(sourceJdbc, targetJdbc, sourceRenderer, targetRenderer);
        ValidationResult result = validator.validate(table, "bank_core");

        assertThat(result.status()).isEqualTo(ValidationStatus.PASS);
        assertThat(result.message()).contains("Skipped LOB columns: [DOC]");
    }
}

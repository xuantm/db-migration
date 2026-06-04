package com.bank.migration.dialect;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.identifier.IdentifierMappingPolicy;
import org.junit.jupiter.api.Test;

class DatabaseDialectTest {

    @Test
    void oracleDialectPreservesUppercaseWithoutQuotes() {
        DatabaseDialect dialect = new OracleDialect();
        assertThat(dialect.renderIdentifier("LIMIT")).isEqualTo("LIMIT");
        assertThat(dialect.renderIdentifier("ACCOUNT_ID")).isEqualTo("ACCOUNT_ID");
        assertThat(dialect.renderQualifiedName("BANK_CORE", "ACCOUNT")).isEqualTo("BANK_CORE.ACCOUNT");
    }

    @Test
    void gaussDialectLowercasesUnquotedIdentifier() {
        DatabaseDialect dialect = new GaussDialect(IdentifierMappingPolicy.QUOTE);
        assertThat(dialect.renderIdentifier("ACCOUNT_ID")).isEqualTo("account_id");
        assertThat(dialect.renderQualifiedName("BANK_CORE", "ACCOUNT")).isEqualTo("bank_core.account");
    }

    @Test
    void gaussDialectQuotesReservedWordUnderQuotePolicy() {
        DatabaseDialect dialect = new GaussDialect(IdentifierMappingPolicy.QUOTE);
        assertThat(dialect.renderIdentifier("LIMIT")).isEqualTo("\"LIMIT\"");
        assertThat(dialect.renderIdentifier("limit")).isEqualTo("\"LIMIT\"");
    }

    @Test
    void gaussDialectRenamesReservedWordUnderRenamePolicy() {
        DatabaseDialect dialect = new GaussDialect(IdentifierMappingPolicy.RENAME);
        assertThat(dialect.renderIdentifier("LIMIT")).isEqualTo("limit_");
        assertThat(dialect.renderIdentifier("limit")).isEqualTo("limit_");
    }

    @Test
    void databaseDialectsDetectReservedWords() {
        DatabaseDialect oracle = new OracleDialect();
        DatabaseDialect gauss = new GaussDialect(IdentifierMappingPolicy.QUOTE);

        assertThat(oracle.isReservedWord("LIMIT")).isTrue();
        assertThat(gauss.isReservedWord("LIMIT")).isTrue();
        assertThat(gauss.isReservedWord("limit")).isTrue();
        assertThat(gauss.isReservedWord("ACCOUNT_ID")).isFalse();
    }
}

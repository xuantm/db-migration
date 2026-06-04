package com.bank.migration.identifier;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.dialect.DatabaseDialect;
import com.bank.migration.dialect.OracleDialect;
import com.bank.migration.dialect.GaussDialect;
import org.junit.jupiter.api.Test;

class IdentifierRendererTest {

    @Test
    void rendersOracleIdentifiers() {
        DatabaseDialect oracle = new OracleDialect();
        IdentifierRenderer renderer = new IdentifierRenderer(oracle);

        assertThat(renderer.render("LIMIT")).isEqualTo("LIMIT");
        assertThat(renderer.render(Identifier.of("ACCOUNT_ID"))).isEqualTo("ACCOUNT_ID");
        assertThat(renderer.renderQualifiedName("BANK_CORE", "ACCOUNT")).isEqualTo("BANK_CORE.ACCOUNT");
    }

    @Test
    void rendersGaussIdentifiersUnderQuotePolicy() {
        DatabaseDialect gauss = new GaussDialect(IdentifierMappingPolicy.QUOTE);
        IdentifierRenderer renderer = new IdentifierRenderer(gauss);

        assertThat(renderer.render("LIMIT")).isEqualTo("\"LIMIT\"");
        assertThat(renderer.render(Identifier.of("ACCOUNT_ID"))).isEqualTo("account_id");
        assertThat(renderer.renderQualifiedName("BANK_CORE", "ACCOUNT")).isEqualTo("bank_core.account");
    }

    @Test
    void rendersGaussIdentifiersUnderRenamePolicy() {
        DatabaseDialect gauss = new GaussDialect(IdentifierMappingPolicy.RENAME);
        IdentifierRenderer renderer = new IdentifierRenderer(gauss);

        assertThat(renderer.render("LIMIT")).isEqualTo("limit_");
        assertThat(renderer.render(Identifier.of("ACCOUNT_ID"))).isEqualTo("account_id");
        assertThat(renderer.renderQualifiedName("BANK_CORE", "ACCOUNT")).isEqualTo("bank_core.account");
    }

    @Test
    void physicalNameBehavesAsExpected() {
        // OracleDialect + USER -> physicalName(USER) == USER
        IdentifierRenderer oracleRenderer = new IdentifierRenderer(new OracleDialect());
        assertThat(oracleRenderer.physicalName("USER")).isEqualTo("USER");

        // GaussDialect(QUOTE) + USER -> physicalName(USER) == USER
        IdentifierRenderer gaussQuoteRenderer = new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.QUOTE));
        assertThat(gaussQuoteRenderer.physicalName("USER")).isEqualTo("USER");

        // GaussDialect(RENAME) + USER -> physicalName(USER) == user_
        IdentifierRenderer gaussRenameRenderer = new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.RENAME));
        assertThat(gaussRenameRenderer.physicalName("USER")).isEqualTo("user_");

        // GaussDialect(QUOTE) + BANK_CORE -> physicalName(BANK_CORE) == bank_core
        assertThat(gaussQuoteRenderer.physicalName("BANK_CORE")).isEqualTo("bank_core");
    }
}

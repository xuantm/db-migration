package com.bank.migration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationManifestTest {
    @Test
    void serializesTablesAndViews() throws Exception {
        MigrationManifest manifest = new MigrationManifest(
            "run-001",
            "BANK_CORE",
            List.of(new TableMetadata(
                "BANK_CORE",
                "ACCOUNT",
                ObjectStatus.READY,
                List.of(new ColumnMetadata("ID", "NUMBER", 19, 0, false, null)),
                List.of(new KeyMetadata("PK_ACCOUNT", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of(new IndexMetadata("IX_ACCOUNT_ID", false, List.of("ID")))
            )),
            List.of(new ViewMetadata("BANK_CORE", "VW_ACCOUNT", ObjectStatus.READY, "select ID from ACCOUNT", List.of("ACCOUNT"), List.of()))
        );

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = mapper.writeValueAsString(manifest);

        assertThat(json).contains("\"sourceSchema\":\"BANK_CORE\"");
        assertThat(json).contains("\"name\":\"ACCOUNT\"");
        assertThat(json).contains("\"name\":\"VW_ACCOUNT\"");
    }
}

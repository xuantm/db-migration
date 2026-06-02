package com.bank.migration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MigrationManifestTest {
    @Test
    void serializesTablesAndViews() throws Exception {
        List<ColumnMetadata> columns = new ArrayList<>(List.of(new ColumnMetadata("ID", "NUMBER", 19, 0, false, null)));
        List<KeyMetadata> keys = new ArrayList<>(List.of(new KeyMetadata("PK_ACCOUNT", "PRIMARY_KEY", List.of("ID"), null, null)));
        List<IndexMetadata> indexes = new ArrayList<>(List.of(new IndexMetadata("IX_ACCOUNT_ID", false, List.of("ID"))));
        List<TableMetadata> tables = new ArrayList<>(List.of(new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            columns,
            keys,
            indexes
        )));
        List<String> dependencies = new ArrayList<>(List.of("ACCOUNT"));
        List<String> notes = new ArrayList<>(List.of("initial import"));
        List<ViewMetadata> views = new ArrayList<>(List.of(new ViewMetadata(
            "BANK_CORE",
            "VW_ACCOUNT",
            ObjectStatus.READY,
            "select ID from ACCOUNT",
            dependencies,
            notes
        )));

        MigrationManifest manifest = new MigrationManifest(
            "run-001",
            "BANK_CORE",
            tables,
            views
        );

        columns.add(new ColumnMetadata("CREATED_AT", "TIMESTAMP", null, null, true, null));
        keys.add(new KeyMetadata("UK_ACCOUNT", "UNIQUE", List.of("ID"), null, null));
        indexes.add(new IndexMetadata("IX_ACCOUNT_CREATED", false, List.of("CREATED_AT")));
        tables.add(new TableMetadata("BANK_CORE", "OTHER", ObjectStatus.WARNING, List.of(), List.of(), List.of()));
        dependencies.add("CUSTOMER");
        notes.add("mutated");
        views.add(new ViewMetadata("BANK_CORE", "VW_OTHER", ObjectStatus.WARNING, "select 1 from dual", List.of(), List.of()));

        assertThat(manifest.tables()).hasSize(1);
        assertThat(manifest.tables().get(0).columns()).hasSize(1);
        assertThat(manifest.tables().get(0).keys()).hasSize(1);
        assertThat(manifest.tables().get(0).indexes()).hasSize(1);
        assertThat(manifest.views()).hasSize(1);
        assertThat(manifest.views().get(0).dependencies()).containsExactly("ACCOUNT");
        assertThat(manifest.views().get(0).notes()).containsExactly("initial import");
        assertThatThrownBy(() -> manifest.tables().add(new TableMetadata("BANK_CORE", "X", ObjectStatus.READY, List.of(), List.of(), List.of())))
            .isInstanceOf(UnsupportedOperationException.class);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(manifest);
        JsonNode root = mapper.readTree(json);

        assertThat(root.path("sourceSchema").asText()).isEqualTo("BANK_CORE");
        assertThat(root.path("tables").size()).isEqualTo(1);
        assertThat(root.path("tables").get(0).path("name").asText()).isEqualTo("ACCOUNT");
        assertThat(root.path("views").size()).isEqualTo(1);
        assertThat(root.path("views").get(0).path("name").asText()).isEqualTo("VW_ACCOUNT");
    }
}

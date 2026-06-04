package com.bank.migration.exclusion;

import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.SchemaObjectMetadata;
import com.bank.migration.domain.SchemaObjectType;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.domain.ViewMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ExclusionResolver {
    private final ExclusionConfig config;

    public ExclusionResolver(ExclusionConfig config) {
        this.config = config;
    }

    public MigrationManifest resolve(MigrationManifest manifest) {
        if (manifest == null) {
            return null;
        }

        Set<String> excludedTables = config.tables().stream()
            .map(t -> t.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

        Set<String> excludedViews = config.views().stream()
            .map(v -> v.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

        Set<String> excludedSequences = config.sequences().stream()
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

        List<TableMetadata> updatedTables = manifest.tables().stream()
            .map(t -> {
                if (excludedTables.contains(t.name().toLowerCase(Locale.ROOT))) {
                    return new TableMetadata(
                        t.schema(),
                        t.name(),
                        ObjectStatus.EXCLUDED,
                        t.columns(),
                        t.keys(),
                        t.indexes(),
                        "Excluded by configuration"
                    );
                }
                return t;
            })
            .toList();

        List<ViewMetadata> updatedViews = manifest.views().stream()
            .map(v -> {
                if (excludedViews.contains(v.name().toLowerCase(Locale.ROOT))) {
                    return new ViewMetadata(
                        v.schema(),
                        v.name(),
                        ObjectStatus.EXCLUDED,
                        v.sql(),
                        v.dependencies(),
                        v.notes(),
                        "Excluded by configuration"
                    );
                }
                List<String> refExcluded = v.dependencies().stream()
                    .filter(dep -> excludedTables.contains(dep.toLowerCase(Locale.ROOT)))
                    .toList();
                if (!refExcluded.isEmpty()) {
                    List<String> notes = new ArrayList<>(v.notes());
                    String refMsg = "References excluded table(s): " + String.join(", ", refExcluded);
                    if (!notes.contains(refMsg)) {
                        notes.add(refMsg);
                    }
                    return new ViewMetadata(
                        v.schema(),
                        v.name(),
                        ObjectStatus.NEEDS_REVIEW,
                        v.sql(),
                        v.dependencies(),
                        notes,
                        refMsg
                    );
                }
                return v;
            })
            .toList();

        List<SchemaObjectMetadata> updatedSchemaObjects = manifest.schemaObjects().stream()
            .map(obj -> {
                if (obj.type() == SchemaObjectType.SEQUENCE &&
                    excludedSequences.contains(obj.name().toLowerCase(Locale.ROOT))) {
                    return new SchemaObjectMetadata(
                        obj.owner(),
                        obj.name(),
                        obj.type(),
                        ObjectStatus.EXCLUDED,
                        obj.notes(),
                        "Excluded by configuration"
                    );
                }
                return obj;
            })
            .toList();

        return new MigrationManifest(
            manifest.runId(),
            manifest.sourceSchema(),
            updatedTables,
            updatedViews,
            updatedSchemaObjects
        );
    }
}

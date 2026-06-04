package com.bank.migration.dataonly;

import com.bank.migration.config.TargetDataPolicy;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.preflight.PreflightCheck;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataOnlyTargetReadinessService {
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;

    public DataOnlyTargetReadinessService(
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
    }

    public List<PreflightCheck> check(MigrationManifest manifest, String targetSchema, TargetDataPolicy policy) {
        List<PreflightCheck> checks = new ArrayList<>();
        for (TableMetadata table : manifest.tables()) {
            if (table.status() == ObjectStatus.EXCLUDED) {
                continue;
            }
            checks.addAll(checkTable(table, targetSchema, policy));
        }
        return List.copyOf(checks);
    }

    private List<PreflightCheck> checkTable(TableMetadata table, String targetSchema, TargetDataPolicy policy) {
        List<PreflightCheck> checks = new ArrayList<>();
        String schema = targetRenderer.physicalName(targetSchema);
        String tableName = targetRenderer.physicalName(table.name());

        Integer exists = targetJdbc.queryForObject(
            "select count(*) from information_schema.tables where table_schema = ? and table_name = ? and table_type = 'BASE TABLE'",
            Integer.class,
            schema,
            tableName
        );
        if (exists == null || exists == 0) {
            checks.add(new PreflightCheck(
                "data-only-target-table-" + table.name(),
                false,
                "Target table " + schema + "." + tableName + " does not exist"
            ));
            return checks;
        }
        checks.add(new PreflightCheck("data-only-target-table-" + table.name(), true, "Target table exists"));

        List<DataOnlyTargetColumn> targetColumns = loadColumns(schema, tableName);
        Set<String> targetColumnNames = new LinkedHashSet<>();
        for (DataOnlyTargetColumn column : targetColumns) {
            targetColumnNames.add(column.name().toLowerCase(Locale.ROOT));
        }
        Set<String> sourceColumnNames = new LinkedHashSet<>();
        for (ColumnMetadata column : table.columns()) {
            sourceColumnNames.add(targetRenderer.physicalName(column.name()).toLowerCase(Locale.ROOT));
        }

        List<String> missingColumns = sourceColumnNames.stream()
            .filter(column -> !targetColumnNames.contains(column))
            .toList();
        checks.add(new PreflightCheck(
            "data-only-target-columns-" + table.name(),
            missingColumns.isEmpty(),
            missingColumns.isEmpty() ? "Target columns are compatible" : "Missing target columns: " + String.join(", ", missingColumns)
        ));

        List<String> unsafeExtraColumns = targetColumns.stream()
            .filter(column -> !sourceColumnNames.contains(column.name().toLowerCase(Locale.ROOT)))
            .filter(column -> !column.canBeOmittedFromInsert())
            .map(DataOnlyTargetColumn::name)
            .toList();
        checks.add(new PreflightCheck(
            "data-only-target-extra-columns-" + table.name(),
            unsafeExtraColumns.isEmpty(),
            unsafeExtraColumns.isEmpty()
                ? "No unsafe extra target columns"
                : "Extra NOT NULL target columns without default/generated value: " + String.join(", ", unsafeExtraColumns)
        ));

        if (policy == TargetDataPolicy.REQUIRE_EMPTY) {
            Long count = targetJdbc.queryForObject(
                "select count(*) from " + targetRenderer.renderQualifiedName(targetSchema, table.name()),
                Long.class
            );
            long rows = count == null ? 0L : count;
            checks.add(new PreflightCheck(
                "data-only-target-empty-" + table.name(),
                rows == 0L,
                rows == 0L
                    ? "Target table is empty"
                    : "Target table " + schema + "." + tableName + " contains " + rows + " rows"
            ));
        }

        return checks;
    }

    private List<DataOnlyTargetColumn> loadColumns(String schema, String tableName) {
        return targetJdbc.query(
            """
            select column_name, is_nullable, column_default, identity_generation, generation_expression
            from information_schema.columns
            where table_schema = ? and table_name = ?
            order by ordinal_position
            """,
            (rs, rowNum) -> new DataOnlyTargetColumn(
                rs.getString("column_name"),
                "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                rs.getString("column_default"),
                rs.getString("identity_generation"),
                rs.getString("generation_expression")
            ),
            schema,
            tableName
        );
    }
}

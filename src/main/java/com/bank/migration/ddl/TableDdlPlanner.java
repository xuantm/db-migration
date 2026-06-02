package com.bank.migration.ddl;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public class TableDdlPlanner {
    private final OracleToGaussTypeMapper typeMapper;

    public TableDdlPlanner(OracleToGaussTypeMapper typeMapper) {
        this.typeMapper = typeMapper;
    }

    public List<DdlStatement> plan(String targetSchema, TableMetadata table) {
        String schemaName = lower(targetSchema);
        String tableName = lower(table.name());
        List<DdlStatement> statements = new ArrayList<>();

        statements.add(new DdlStatement("TABLE", table.name(), buildCreateTableSql(schemaName, tableName, table.columns())));

        for (KeyMetadata key : table.keys()) {
            if ("PRIMARY_KEY".equalsIgnoreCase(key.type()) || "UNIQUE".equalsIgnoreCase(key.type())) {
                statements.add(new DdlStatement("CONSTRAINT", key.name(), buildKeyConstraintSql(schemaName, tableName, key)));
            }
        }

        for (IndexMetadata index : table.indexes()) {
            statements.add(new DdlStatement("INDEX", index.name(), buildIndexSql(schemaName, tableName, index)));
        }

        for (KeyMetadata key : table.keys()) {
            if ("FOREIGN_KEY".equalsIgnoreCase(key.type())) {
                statements.add(new DdlStatement("CONSTRAINT", key.name(), buildForeignKeySql(schemaName, tableName, key, targetSchema)));
            }
        }

        return List.copyOf(statements);
    }

    private String buildCreateTableSql(String schemaName, String tableName, List<ColumnMetadata> columns) {
        StringJoiner joiner = new StringJoiner(",\n  ", "create table " + schemaName + "." + tableName + " (\n  ", "\n)");
        for (ColumnMetadata column : columns) {
            GaussType mapped = typeMapper.map(column);
            String definition = lower(column.name()) + " " + mapped.sqlType();
            if (!column.nullable()) {
                definition += " not null";
            }
            joiner.add(definition);
        }
        return joiner.toString();
    }

    private String buildKeyConstraintSql(String schemaName, String tableName, KeyMetadata key) {
        String columns = joinColumns(key.columns());
        String type = "UNIQUE".equalsIgnoreCase(key.type()) ? "unique" : "primary key";
        return "alter table " + schemaName + "." + tableName + " add constraint " + lower(key.name()) + " " + type + " (" + columns + ")";
    }

    private String buildForeignKeySql(String schemaName, String tableName, KeyMetadata key, String targetSchema) {
        String columns = joinColumns(key.columns());
        String referencedColumns = joinColumns(key.referencedColumns());
        return "alter table "
            + schemaName
            + "."
            + tableName
            + " add constraint "
            + lower(key.name())
            + " foreign key ("
            + columns
            + ") references "
            + lower(targetSchema)
            + "."
            + lower(key.referencedTable())
            + " ("
            + referencedColumns
            + ")";
    }

    private String buildIndexSql(String schemaName, String tableName, IndexMetadata index) {
        String prefix = index.unique() ? "create unique index " : "create index ";
        return prefix + lower(index.name()) + " on " + schemaName + "." + tableName + " (" + joinColumns(index.columns()) + ")";
    }

    private static String joinColumns(List<String> columns) {
        return String.join(", ", columns.stream().map(TableDdlPlanner::lower).toList());
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

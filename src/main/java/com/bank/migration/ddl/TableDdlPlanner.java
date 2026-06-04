package com.bank.migration.ddl;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.types.OracleToGaussTypeMapper;
import com.bank.migration.types.TypeMappingResult;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TableDdlPlanner {
    private final OracleToGaussTypeMapper typeMapper;
    private final IdentifierRenderer targetRenderer;

    public TableDdlPlanner(
        OracleToGaussTypeMapper typeMapper,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.typeMapper = typeMapper;
        this.targetRenderer = targetRenderer;
    }

    public List<DdlStatement> plan(String targetSchema, TableMetadata table) {
        if (table.columns().isEmpty()) {
            throw new IllegalArgumentException("Table " + table.name() + " has no columns");
        }
        List<DdlStatement> statements = new ArrayList<>();

        statements.add(new DdlStatement("TABLE", table.name(), buildCreateTableSql(targetSchema, table.name(), table.columns(), table.name())));

        for (KeyMetadata key : table.keys()) {
            if ("PRIMARY_KEY".equalsIgnoreCase(key.type()) || "UNIQUE".equalsIgnoreCase(key.type())) {
                statements.add(new DdlStatement("CONSTRAINT", key.name(), buildKeyConstraintSql(targetSchema, table.name(), key)));
            }
        }

        for (IndexMetadata index : table.indexes()) {
            statements.add(new DdlStatement("INDEX", index.name(), buildIndexSql(targetSchema, table.name(), index)));
        }

        for (KeyMetadata key : table.keys()) {
            if ("FOREIGN_KEY".equalsIgnoreCase(key.type())) {
                statements.add(new DdlStatement("FOREIGN_KEY", key.name(), buildForeignKeySql(targetSchema, table.name(), key)));
            }
        }

        return List.copyOf(statements);
    }

    private String buildCreateTableSql(String targetSchema, String tableName, List<ColumnMetadata> columns, String tableDisplayName) {
        StringJoiner joiner = new StringJoiner(",\n  ", "create table " + targetRenderer.renderQualifiedName(targetSchema, tableName) + " (\n  ", "\n)");
        for (ColumnMetadata column : columns) {
            joiner.add(columnSql(column, tableDisplayName));
        }
        return joiner.toString();
    }

    private String columnSql(ColumnMetadata column, String tableName) {
        TypeMappingResult mapped = typeMapper.map(column);
        if (!mapped.canGenerateDdl()) {
            throw new IllegalArgumentException(
                "Column " + column.name() + " in table " + tableName + " cannot generate DDL: " + mapped.notes()
            );
        }
        String definition = targetRenderer.render(column.name()) + " " + mapped.targetSqlType();
        if (!column.nullable()) {
            definition += " not null";
        }
        return definition;
    }

    private String buildKeyConstraintSql(String targetSchema, String tableName, KeyMetadata key) {
        String columns = joinColumns(key.columns());
        String type = "UNIQUE".equalsIgnoreCase(key.type()) ? "unique" : "primary key";
        return "alter table " + targetRenderer.renderQualifiedName(targetSchema, tableName)
            + " add constraint " + targetRenderer.render(key.name()) + " " + type + " (" + columns + ")";
    }

    private String buildForeignKeySql(String targetSchema, String tableName, KeyMetadata key) {
        String columns = joinColumns(key.columns());
        String referencedColumns = joinColumns(key.referencedColumns());
        String sql = "alter table "
            + targetRenderer.renderQualifiedName(targetSchema, tableName)
            + " add constraint "
            + targetRenderer.render(key.name())
            + " foreign key ("
            + columns
            + ") references "
            + targetRenderer.renderQualifiedName(targetSchema, key.referencedTable())
            + " ("
            + referencedColumns
            + ")";
        if (!key.validated()) {
            sql += " not valid";
        }
        return sql;
    }

    private String buildIndexSql(String targetSchema, String tableName, IndexMetadata index) {
        String prefix = index.unique() ? "create unique index " : "create index ";
        return prefix + targetRenderer.render(index.name()) + " on "
            + targetRenderer.renderQualifiedName(targetSchema, tableName) + " (" + joinColumns(index.columns()) + ")";
    }

    private String joinColumns(List<String> columns) {
        return String.join(", ", columns.stream().map(targetRenderer::render).toList());
    }
}

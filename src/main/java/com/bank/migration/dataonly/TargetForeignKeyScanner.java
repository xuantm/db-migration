package com.bank.migration.dataonly;

import com.bank.migration.identifier.IdentifierRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TargetForeignKeyScanner {
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;

    public TargetForeignKeyScanner(
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
    }

    public List<TargetForeignKeyMetadata> scan(String targetSchema, Set<String> includedTables) {
        String sql = """
            select
              con.conname,
              child_ns.nspname as child_schema,
              child.relname as child_table,
              parent_ns.nspname as parent_schema,
              parent.relname as parent_table,
              child_att.attname as child_column,
              parent_att.attname as parent_column,
              ord.n as column_position,
              con.confmatchtype
            from pg_catalog.pg_constraint con
            join pg_catalog.pg_class child on child.oid = con.conrelid
            join pg_catalog.pg_namespace child_ns on child_ns.oid = child.relnamespace
            join pg_catalog.pg_class parent on parent.oid = con.confrelid
            join pg_catalog.pg_namespace parent_ns on parent_ns.oid = parent.relnamespace
            join unnest(con.conkey, con.confkey) with ordinality as ord(child_attnum, parent_attnum, n) on true
            join pg_catalog.pg_attribute child_att on child_att.attrelid = child.oid and child_att.attnum = ord.child_attnum
            join pg_catalog.pg_attribute parent_att on parent_att.attrelid = parent.oid and parent_att.attnum = ord.parent_attnum
            where con.contype = 'f'
              and child_ns.nspname = ?
            order by con.conname, child.relname, ord.n
            """;

        String physicalSchema = targetRenderer.physicalName(targetSchema);
        List<FkColumnRow> rows = targetJdbc.query(sql, (rs, rowNum) -> new FkColumnRow(
            rs.getString("conname"),
            rs.getString("child_schema"),
            rs.getString("child_table"),
            rs.getString("parent_schema"),
            rs.getString("parent_table"),
            rs.getString("child_column"),
            rs.getString("parent_column"),
            rs.getString("confmatchtype")
        ), physicalSchema);

        List<TargetForeignKeyMetadata> result = new ArrayList<>();
        String current = null;
        String childSchema = null;
        String childTable = null;
        String parentSchema = null;
        String parentTable = null;
        List<String> childColumns = new ArrayList<>();
        List<String> parentColumns = new ArrayList<>();
        String confmatchtype = null;

        for (FkColumnRow row : rows) {
            if (!includedTables.contains(row.childTable().toUpperCase())) {
                continue;
            }
            if (current != null && (!current.equals(row.constraintName()) || !childTable.equals(row.childTable()))) {
                result.add(new TargetForeignKeyMetadata(current, childSchema, childTable, childColumns, parentSchema, parentTable, parentColumns, confmatchtype));
                childColumns = new ArrayList<>();
                parentColumns = new ArrayList<>();
            }
            current = row.constraintName();
            childSchema = row.childSchema();
            childTable = row.childTable();
            parentSchema = row.parentSchema();
            parentTable = row.parentTable();
            childColumns.add(row.childColumn());
            parentColumns.add(row.parentColumn());
            confmatchtype = row.confmatchtype();
        }
        if (current != null) {
            result.add(new TargetForeignKeyMetadata(current, childSchema, childTable, childColumns, parentSchema, parentTable, parentColumns, confmatchtype));
        }
        return List.copyOf(result);
    }

    private record FkColumnRow(
        String constraintName,
        String childSchema,
        String childTable,
        String parentSchema,
        String parentTable,
        String childColumn,
        String parentColumn,
        String confmatchtype
    ) {}
}

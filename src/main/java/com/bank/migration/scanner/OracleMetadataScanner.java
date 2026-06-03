package com.bank.migration.scanner;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.domain.ViewMetadata;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class OracleMetadataScanner {
    public static final String TABLE_SQL = """
        select table_name
        from all_tables
        where owner = ?
        order by table_name
        """;

    public static final String COLUMN_SQL = """
        select table_name, column_name, data_type,
               case
                 when data_type in ('VARCHAR2', 'NVARCHAR2', 'CHAR', 'NCHAR') then coalesce(char_length, data_length)
                 else data_precision
               end as data_precision,
               data_scale, nullable, data_default
        from all_tab_columns
        where owner = ?
        order by table_name, column_id
        """;

    public static final String KEY_SQL = """
        select c.table_name, c.constraint_name, c.constraint_type, c.validated, cc.column_name,
               r.table_name as referenced_table_name, rcc.column_name as referenced_column_name
        from all_constraints c
        join all_cons_columns cc on cc.owner = c.owner and cc.constraint_name = c.constraint_name
        left join all_constraints r on r.owner = c.r_owner and r.constraint_name = c.r_constraint_name
        left join all_cons_columns rcc on rcc.owner = r.owner and rcc.constraint_name = r.constraint_name and rcc.position = cc.position
        where c.owner = ? and c.constraint_type in ('P', 'U', 'R')
        order by c.table_name, c.constraint_name, cc.position
        """;

    public static final String INDEX_SQL = """
        select i.table_name, i.index_name, i.uniqueness, ic.column_name
        from all_indexes i
        join all_ind_columns ic on ic.index_owner = i.owner and ic.index_name = i.index_name
        where i.owner = ?
          and not exists (
            select 1
            from all_constraints c
            where c.owner = i.owner
              and c.table_name = i.table_name
              and c.index_name = i.index_name
              and c.constraint_type in ('P', 'U')
          )
        order by i.table_name, i.index_name, ic.column_position
        """;

    public static final String VIEW_SQL = """
        select view_name, text
        from all_views
        where owner = ?
        order by view_name
        """;

    static final RowMapper<ColumnRow> COLUMN_ROW_MAPPER = (rs, rowNum) -> new ColumnRow(
        rs.getString("table_name"),
        rs.getString("column_name"),
        rs.getString("data_type"),
        toInteger(rs.getObject("data_precision")),
        toInteger(rs.getObject("data_scale")),
        rs.getString("nullable"),
        rs.getString("data_default")
    );

    private final JdbcTemplate jdbc;

    public OracleMetadataScanner(@Qualifier("sourceJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public MigrationManifest scan(String runId, String sourceSchema) {
        String oracleOwner = normalizeOracleOwner(sourceSchema);

        List<String> tableNames = jdbc.query(TABLE_SQL, (rs, rowNum) -> rs.getString("table_name"), oracleOwner);
        List<ColumnRow> columns = jdbc.query(COLUMN_SQL, COLUMN_ROW_MAPPER, oracleOwner);
        List<KeyRow> keys = jdbc.query(KEY_SQL, (rs, rowNum) -> new KeyRow(
            rs.getString("table_name"),
            rs.getString("constraint_name"),
            normalizeConstraintType(rs.getString("constraint_type")),
            rs.getString("validated"),
            rs.getString("column_name"),
            rs.getString("referenced_table_name"),
            rs.getString("referenced_column_name")
        ), oracleOwner);
        List<IndexRow> indexes = jdbc.query(INDEX_SQL, (rs, rowNum) -> new IndexRow(
            rs.getString("table_name"),
            rs.getString("index_name"),
            rs.getString("uniqueness"),
            rs.getString("column_name")
        ), oracleOwner);
        // The current scanner intentionally reads ALL_VIEWS.TEXT only; later view planning/reporting
        // will flag unsupported Oracle view SQL for review.
        List<ViewRow> views = jdbc.query(VIEW_SQL, (rs, rowNum) -> new ViewRow(
            rs.getString("view_name"),
            rs.getString("text")
        ), oracleOwner);

        List<TableMetadata> tables = tableNames.stream()
            .map(table -> {
                List<ColumnMetadata> tableCols = toColumns(table, columns);
                return new TableMetadata(
                    oracleOwner,
                    table,
                    ObjectStatus.READY,
                    tableCols,
                    toKeys(table, keys),
                    toIndexes(table, indexes, tableCols)
                );
            })
            .toList();

        List<ViewMetadata> viewMetadata = views.stream()
            .map(view -> new ViewMetadata(oracleOwner, view.name(), ObjectStatus.READY, view.sql(), List.of(), List.of()))
            .toList();

        return new MigrationManifest(runId, oracleOwner, tables, viewMetadata);
    }

    private static List<ColumnMetadata> toColumns(String table, List<ColumnRow> rows) {
        return rows.stream()
            .filter(row -> row.tableName().equals(table))
            .map(row -> new ColumnMetadata(
                row.columnName(),
                row.dataType(),
                row.precision(),
                row.scale(),
                "Y".equals(row.nullable()),
                row.dataDefault()
            ))
            .toList();
    }

    private static List<KeyMetadata> toKeys(String table, List<KeyRow> rows) {
        Map<String, List<KeyRow>> grouped = rows.stream()
            .filter(row -> row.tableName().equals(table))
            .collect(Collectors.groupingBy(KeyRow::constraintName, LinkedHashMap::new, Collectors.toList()));
        List<KeyMetadata> keys = new ArrayList<>();
        for (List<KeyRow> group : grouped.values()) {
            KeyRow first = group.getFirst();
            boolean isValidated = !"NOT VALIDATED".equalsIgnoreCase(first.validated());
            keys.add(new KeyMetadata(
                first.constraintName(),
                first.constraintType(),
                group.stream().map(KeyRow::columnName).toList(),
                first.referencedTableName(),
                group.stream().map(KeyRow::referencedColumnName).filter(value -> value != null).toList(),
                isValidated
            ));
        }
        return keys;
    }

    private static List<IndexMetadata> toIndexes(String table, List<IndexRow> rows, List<ColumnMetadata> tableCols) {
        java.util.Set<String> colNames = tableCols.stream()
            .map(c -> c.name().toUpperCase(Locale.ROOT))
            .collect(Collectors.toSet());

        Map<String, List<IndexRow>> grouped = rows.stream()
            .filter(row -> row.tableName().equals(table))
            .collect(Collectors.groupingBy(IndexRow::indexName, LinkedHashMap::new, Collectors.toList()));
        List<IndexMetadata> indexes = new ArrayList<>();
        for (List<IndexRow> group : grouped.values()) {
            IndexRow first = group.getFirst();
            List<String> indexCols = group.stream().map(IndexRow::columnName).toList();
            
            boolean allColsExist = indexCols.stream()
                .allMatch(col -> colNames.contains(col.toUpperCase(Locale.ROOT)));
                
            if (allColsExist) {
                indexes.add(new IndexMetadata(
                    first.indexName(),
                    "UNIQUE".equalsIgnoreCase(first.uniqueness()),
                    indexCols
                ));
            }
        }
        return indexes;
    }

    private static String normalizeConstraintType(String oracleType) {
        return switch (oracleType) {
            case "P" -> "PRIMARY_KEY";
            case "U" -> "UNIQUE";
            case "R" -> "FOREIGN_KEY";
            default -> oracleType;
        };
    }

    static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return toExactInteger(number.toString(), value);
        }
        if (value instanceof String text) {
            return toExactInteger(text, value);
        }
        throw new IllegalArgumentException("Unsupported numeric metadata value type: " + value.getClass().getName());
    }

    private static Integer toExactInteger(String value, Object originalValue) {
        try {
            return new BigDecimal(value.trim()).stripTrailingZeros().intValueExact();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Unsupported numeric metadata value: " + originalValue, ex);
        }
    }

    private static String normalizeOracleOwner(String sourceSchema) {
        return sourceSchema.toUpperCase(Locale.ROOT);
    }

    public record ColumnRow(String tableName, String columnName, String dataType, Integer precision, Integer scale, String nullable, String dataDefault) {}
    public record KeyRow(String tableName, String constraintName, String constraintType, String validated, String columnName, String referencedTableName, String referencedColumnName) {
        public KeyRow(String tableName, String constraintName, String constraintType, String columnName, String referencedTableName, String referencedColumnName) {
            this(tableName, constraintName, constraintType, "VALIDATED", columnName, referencedTableName, referencedColumnName);
        }
    }
    public record IndexRow(String tableName, String indexName, String uniqueness, String columnName) {}
    public record ViewRow(String name, String sql) {}
}

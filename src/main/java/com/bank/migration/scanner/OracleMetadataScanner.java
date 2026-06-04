package com.bank.migration.scanner;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.SchemaObjectMetadata;
import com.bank.migration.domain.SchemaObjectType;
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
    public static final String SCANNER_CONTRACT_VERSION = "oracle-metadata-scan-v1";

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
        select i.table_name, i.index_name, i.uniqueness, ic.column_name, ie.column_expression, i.index_type
        from all_indexes i
        join all_ind_columns ic on ic.index_owner = i.owner and ic.index_name = i.index_name and ic.table_name = i.table_name
        left join all_ind_expressions ie on ie.index_owner = ic.index_owner and ie.index_name = ic.index_name and ie.table_name = ic.table_name and ie.column_position = ic.column_position
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

    public static final String VIEW_DEPENDENCY_SQL = """
        select name, referenced_name
        from all_dependencies
        where owner = ?
          and type = 'VIEW'
          and referenced_type = 'TABLE'
          and referenced_owner = ?
        order by name, referenced_name
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
            rs.getString("column_name"),
            rs.getString("column_expression"),
            rs.getString("index_type")
        ), oracleOwner);
        // The current scanner intentionally reads ALL_VIEWS.TEXT only; later view planning/reporting
        // will flag unsupported Oracle view SQL for review.
        List<ViewRow> views = jdbc.query(VIEW_SQL, (rs, rowNum) -> new ViewRow(
            rs.getString("view_name"),
            rs.getString("text")
        ), oracleOwner);

        List<ViewDependencyRow> dependencies = jdbc.query(VIEW_DEPENDENCY_SQL, (rs, rowNum) -> new ViewDependencyRow(
            rs.getString("name"),
            rs.getString("referenced_name")
        ), oracleOwner, oracleOwner);

        Map<String, List<String>> viewDeps = dependencies.stream()
            .collect(Collectors.groupingBy(
                ViewDependencyRow::viewName,
                Collectors.mapping(ViewDependencyRow::tableName, Collectors.toList())
            ));

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
            .map(view -> {
                List<String> deps = viewDeps.getOrDefault(view.name(), List.of());
                return new ViewMetadata(oracleOwner, view.name(), ObjectStatus.READY, view.sql(), deps, List.of());
            })
            .toList();

        List<SchemaObjectMetadata> schemaObjects = new ArrayList<>();

        // Query sequences, synonyms, triggers, procedures, functions, packages, types
        List<Map<String, Object>> rawObjects = jdbc.query(
            "select object_name, object_type, status from all_objects where owner = ? " +
            "and object_type in ('SEQUENCE', 'SYNONYM', 'MATERIALIZED VIEW', 'TRIGGER', 'PROCEDURE', 'FUNCTION', 'PACKAGE', 'TYPE') " +
            "order by object_type, object_name",
            (rs, rn) -> Map.of(
                "object_name", rs.getString("object_name"),
                "object_type", rs.getString("object_type"),
                "status", rs.getString("status")
            ),
            oracleOwner
        );
        for (Map<String, Object> row : rawObjects) {
            String name = (String) row.get("object_name");
            String typeStr = (String) row.get("object_type");
            String statusStr = (String) row.get("status");

            SchemaObjectType type;
            ObjectStatus status = "VALID".equalsIgnoreCase(statusStr) ? ObjectStatus.READY : ObjectStatus.WARNING;
            List<String> notes = new ArrayList<>();

            if ("SEQUENCE".equals(typeStr)) {
                type = SchemaObjectType.SEQUENCE;
            } else if ("SYNONYM".equals(typeStr)) {
                type = SchemaObjectType.SYNONYM;
                status = ObjectStatus.NEEDS_REVIEW;
                notes.add("Synonym is out of scope and will not be migrated.");
            } else if ("MATERIALIZED VIEW".equals(typeStr)) {
                type = SchemaObjectType.MATERIALIZED_VIEW;
                status = ObjectStatus.NEEDS_REVIEW;
                notes.add("Materialized view is out of scope and will not be migrated.");
            } else if ("TRIGGER".equals(typeStr)) {
                type = SchemaObjectType.TRIGGER;
                status = ObjectStatus.NEEDS_REVIEW;
                notes.add("Trigger is out of scope and will not be migrated.");
            } else if ("PROCEDURE".equals(typeStr)) {
                type = SchemaObjectType.PROCEDURE;
                status = ObjectStatus.NEEDS_REVIEW;
                notes.add("Procedure is out of scope and will not be migrated.");
            } else if ("FUNCTION".equals(typeStr)) {
                type = SchemaObjectType.FUNCTION;
                status = ObjectStatus.NEEDS_REVIEW;
                notes.add("Function is out of scope and will not be migrated.");
            } else if ("PACKAGE".equals(typeStr)) {
                type = SchemaObjectType.PACKAGE;
                status = ObjectStatus.NEEDS_REVIEW;
                notes.add("Package is out of scope and will not be migrated.");
            } else if ("TYPE".equals(typeStr)) {
                type = SchemaObjectType.TYPE;
                status = ObjectStatus.NEEDS_REVIEW;
                notes.add("Type is out of scope and will not be migrated.");
            } else {
                continue;
            }

            schemaObjects.add(new SchemaObjectMetadata(oracleOwner, name, type, status, notes));
        }

        // Query check constraints
        List<Map<String, Object>> rawConstraints = jdbc.query(
            "select constraint_name, table_name, status, search_condition from all_constraints where owner = ? and constraint_type = 'C' order by constraint_name",
            (rs, rn) -> {
                String cname = rs.getString("constraint_name");
                String tname = rs.getString("table_name");
                String status = rs.getString("status");
                String searchCond = rs.getString("search_condition");
                return Map.of(
                    "constraint_name", cname != null ? cname : "",
                    "table_name", tname != null ? tname : "",
                    "status", status != null ? status : "",
                    "search_condition", searchCond != null ? searchCond : ""
                );
            },
            oracleOwner
        );
        for (Map<String, Object> row : rawConstraints) {
            String name = (String) row.get("constraint_name");
            String tableName = (String) row.get("table_name");
            String statusStr = (String) row.get("status");
            String searchCondition = (String) row.get("search_condition");

            if (isGeneratedNotNullConstraint(searchCondition)) {
                continue;
            }

            ObjectStatus status = "ENABLED".equalsIgnoreCase(statusStr) ? ObjectStatus.READY : ObjectStatus.WARNING;
            schemaObjects.add(new SchemaObjectMetadata(
                oracleOwner,
                name,
                SchemaObjectType.CHECK_CONSTRAINT,
                status,
                List.of("Check constraint on table " + tableName)
            ));
        }

        // Query function-based indexes and bitmap indexes
        Map<String, List<IndexRow>> indexGroup = indexes.stream()
            .collect(Collectors.groupingBy(IndexRow::indexName));
        for (Map.Entry<String, List<IndexRow>> entry : indexGroup.entrySet()) {
            String indexName = entry.getKey();
            List<IndexRow> group = entry.getValue();
            IndexRow first = group.getFirst();

            boolean isBitmap = "BITMAP".equalsIgnoreCase(first.indexType());
            boolean isFunctionBased = group.stream().anyMatch(r -> r.columnExpression() != null && !r.columnExpression().isBlank());

            if (isFunctionBased) {
                schemaObjects.add(new SchemaObjectMetadata(
                    oracleOwner,
                    indexName,
                    SchemaObjectType.FUNCTION_BASED_INDEX,
                    ObjectStatus.NEEDS_REVIEW,
                    List.of("Function-based index on table " + first.tableName())
                ));
            }
            if (isBitmap) {
                schemaObjects.add(new SchemaObjectMetadata(
                    oracleOwner,
                    indexName,
                    SchemaObjectType.BITMAP_INDEX,
                    ObjectStatus.NEEDS_REVIEW,
                    List.of("Bitmap index on table " + first.tableName())
                ));
            }
        }

        return new MigrationManifest(runId, oracleOwner, tables, viewMetadata, schemaObjects);
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
            boolean isBitmap = "BITMAP".equalsIgnoreCase(first.indexType());
            boolean isFunctionBased = group.stream().anyMatch(r -> r.columnExpression() != null && !r.columnExpression().isBlank());
            if (isBitmap || isFunctionBased) {
                continue;
            }

            List<String> indexCols = new ArrayList<>();
            boolean allColsValid = true;
            for (IndexRow row : group) {
                String expr = row.columnExpression();
                if (expr != null && !expr.isBlank()) {
                    indexCols.add(expr);
                } else {
                    String colName = row.columnName();
                    indexCols.add(colName);
                    if (colName == null || !colNames.contains(colName.toUpperCase(Locale.ROOT))) {
                        allColsValid = false;
                    }
                }
            }
            
            if (allColsValid) {
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

    private static final java.util.regex.Pattern NOT_NULL_PATTERN = java.util.regex.Pattern.compile(
        "^(\"[^\"]+\"|[A-Za-z0-9_#$]+)\\s+IS\\s+NOT\\s+NULL$", 
        java.util.regex.Pattern.CASE_INSENSITIVE
    );

    private static boolean isGeneratedNotNullConstraint(String searchCondition) {
        if (searchCondition == null || searchCondition.isBlank()) {
            return false;
        }
        return NOT_NULL_PATTERN.matcher(searchCondition.trim()).matches();
    }

    public record ColumnRow(String tableName, String columnName, String dataType, Integer precision, Integer scale, String nullable, String dataDefault) {}
    public record KeyRow(String tableName, String constraintName, String constraintType, String validated, String columnName, String referencedTableName, String referencedColumnName) {
        public KeyRow(String tableName, String constraintName, String constraintType, String columnName, String referencedTableName, String referencedColumnName) {
            this(tableName, constraintName, constraintType, "VALIDATED", columnName, referencedTableName, referencedColumnName);
        }
    }
    public record IndexRow(String tableName, String indexName, String uniqueness, String columnName, String columnExpression, String indexType) {
        public IndexRow(String tableName, String indexName, String uniqueness, String columnName, String columnExpression) {
            this(tableName, indexName, uniqueness, columnName, columnExpression, "NORMAL");
        }
    }
    public record ViewRow(String name, String sql) {}
    public record ViewDependencyRow(String viewName, String tableName) {}
}

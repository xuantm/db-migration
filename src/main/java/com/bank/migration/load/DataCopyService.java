package com.bank.migration.load;

import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.TableMetadata;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataCopyService {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;

    public DataCopyService(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
    }

    public TableCopyResult copyChunk(TableMetadata table, String targetSchema, ChunkPlan chunk) {
        List<String> sourceColumns = table.columns().stream().map(ColumnMetadata::name).toList();
        String sourceSql = "select " + String.join(", ", sourceColumns)
            + " from " + table.schema() + "." + table.name()
            + " where " + chunk.whereClause();

        List<Map<String, Object>> rows = sourceJdbc.query(sourceSql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String column : sourceColumns) {
                Object value = rs.getObject(column);
                if (value != null) {
                    String className = value.getClass().getName();
                    if (className.startsWith("oracle.sql.INTERVAL")) {
                        value = value.toString();
                    } else if (className.startsWith("oracle.sql.TIMESTAMP")) {
                        value = rs.getTimestamp(column);
                    } else if (className.startsWith("oracle.sql.DATE")) {
                        value = rs.getTimestamp(column);
                    }
                }
                row.put(column, value);
            }
            return row;
        });

        if (rows.isEmpty()) {
            return new TableCopyResult(0, 0);
        }

        String insertSql = insertSql(targetSchema, table.name(), sourceColumns);
        List<Object[]> args = rows.stream()
            .map(row -> sourceColumns.stream().map(row::get).toArray())
            .toList();

        int[] counts = targetJdbc.batchUpdate(insertSql, args);
        return new TableCopyResult(rows.size(), countWrittenRows(rows.size(), counts));
    }

    private static String insertSql(String targetSchema, String tableName, List<String> sourceColumns) {
        List<String> targetColumns = sourceColumns.stream().map(DataCopyService::lower).toList();
        String placeholders = String.join(", ", targetColumns.stream().map(column -> "?").toList());
        return "insert into " + lower(targetSchema) + "." + lower(tableName)
            + " (" + String.join(", ", targetColumns) + ") values (" + placeholders + ")";
    }

    private static long countWrittenRows(int expectedRows, int[] counts) {
        if (counts == null || counts.length == 0) {
            return expectedRows;
        }

        long written = 0;
        for (int count : counts) {
            if (count == Statement.SUCCESS_NO_INFO) {
                written++;
            } else if (count >= 0) {
                written += count;
            }
        }
        return written;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}

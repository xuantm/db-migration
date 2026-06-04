package com.bank.migration.load;

import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.load.conversion.SourceValueConverter;
import com.bank.migration.load.conversion.LobConversionException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DataCopyService {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer sourceRenderer;
    private final IdentifierRenderer targetRenderer;
    private final SourceValueConverter valueConverter;

    public DataCopyService(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("sourceIdentifierRenderer") IdentifierRenderer sourceRenderer,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer,
        SourceValueConverter valueConverter
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
        this.sourceRenderer = sourceRenderer;
        this.targetRenderer = targetRenderer;
        this.valueConverter = valueConverter;
    }

    public TableCopyResult copyChunk(TableMetadata table, String targetSchema, ChunkPlan chunk) {
        List<String> rawColumns = table.columns().stream().map(ColumnMetadata::name).toList();
        List<String> renderedSourceColumns = rawColumns.stream().map(sourceRenderer::render).toList();

        String sourceSql = "select " + String.join(", ", renderedSourceColumns)
            + " from " + sourceRenderer.renderQualifiedName(table.schema(), table.name())
            + " where " + chunk.whereClause();

        List<Map<String, Object>> rows = sourceJdbc.query(sourceSql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < rawColumns.size(); i++) {
                String rawCol = rawColumns.get(i);
                String renderedCol = renderedSourceColumns.get(i);
                Object value;
                try {
                    Object rawValue = rs.getObject(renderedCol);
                    value = valueConverter.convert(rs, renderedCol, rawValue);
                } catch (Exception ex) {
                    Throwable cause = ex;
                    if (ex instanceof LobConversionException && ex.getCause() != null) {
                        cause = ex.getCause();
                    }
                    throw new LobConversionException(
                        "Phase: DATA_COPY, Table: " + table.name() + ", Chunk: " + chunk.chunkId() +
                        ", Column: " + rawCol + ", Cause: " + cause.getClass().getName(), ex
                    );
                }
                row.put(rawCol, value);
            }
            return row;
        });

        if (rows.isEmpty()) {
            return new TableCopyResult(0, 0);
        }

        String insertSql = insertSql(targetSchema, table.name(), rawColumns);
        List<Object[]> args = rows.stream()
            .map(row -> rawColumns.stream().map(row::get).toArray())
            .toList();

        int[] counts = targetJdbc.batchUpdate(insertSql, args);
        return new TableCopyResult(rows.size(), countWrittenRows(rows.size(), counts));
    }

    private String insertSql(String targetSchema, String tableName, List<String> rawColumns) {
        List<String> targetColumns = rawColumns.stream().map(targetRenderer::render).toList();
        String placeholders = String.join(", ", targetColumns.stream().map(column -> "?").toList());
        return "insert into " + targetRenderer.renderQualifiedName(targetSchema, tableName)
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
}

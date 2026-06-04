package com.bank.migration.validate;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class SampleRowValidator {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer sourceRenderer;
    private final IdentifierRenderer targetRenderer;
    private int sampleSize = 100;

    public SampleRowValidator(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("sourceIdentifierRenderer") IdentifierRenderer sourceRenderer,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
        this.sourceRenderer = sourceRenderer;
        this.targetRenderer = targetRenderer;
    }

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    public ValidationResult validate(TableMetadata table, String targetSchema) {
        List<String> scalarCols = new ArrayList<>();
        List<String> lobCols = new ArrayList<>();

        for (ColumnMetadata col : table.columns()) {
            if (isLobType(col.oracleType())) {
                lobCols.add(col.name());
            } else {
                scalarCols.add(col.name());
            }
        }

        if (scalarCols.isEmpty()) {
            return new ValidationResult(
                "sample-row",
                ValidationStatus.PASS,
                table.name(),
                "No scalar columns to compare. Skipped LOBs: " + lobCols
            );
        }

        List<String> pkCols = table.keys().stream()
            .filter(k -> "PRIMARY_KEY".equalsIgnoreCase(k.type()))
            .flatMap(k -> k.columns().stream())
            .toList();

        List<String> sortCols = !pkCols.isEmpty() ? pkCols : scalarCols;

        String sourceSelect = buildSelect(table.schema(), table.name(), scalarCols, sortCols, sourceRenderer);
        String targetSelect = buildSelect(targetSchema, table.name(), scalarCols, sortCols, targetRenderer);

        List<List<Object>> sourceRows = fetchSample(sourceJdbc, sourceSelect, scalarCols.size(), sampleSize);
        List<List<Object>> targetRows = fetchSample(targetJdbc, targetSelect, scalarCols.size(), sampleSize);

        boolean pass = sourceRows.equals(targetRows);
        String details = "Compared up to " + sampleSize + " rows. sourceRows=" + sourceRows.size() + " targetRows=" + targetRows.size();
        if (!pass) {
            details += " - mismatch found!";
        }
        if (!lobCols.isEmpty()) {
            details += " (Skipped LOB columns: " + lobCols + ")";
        }

        return new ValidationResult(
            "sample-row",
            pass ? ValidationStatus.PASS : ValidationStatus.FAIL,
            table.name(),
            details
        );
    }

    private String buildSelect(String schema, String tableName, List<String> columns, List<String> sortCols, IdentifierRenderer renderer) {
        StringBuilder sb = new StringBuilder("select ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(renderer.render(columns.get(i)));
        }
        sb.append(" from ").append(renderer.renderQualifiedName(schema, tableName));
        if (!sortCols.isEmpty()) {
            sb.append(" order by ");
            for (int i = 0; i < sortCols.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderer.render(sortCols.get(i)));
            }
        }
        return sb.toString();
    }

    private List<List<Object>> fetchSample(JdbcTemplate jdbc, String sql, int colCount, int limit) {
        return jdbc.query(sql, rs -> {
            List<List<Object>> rows = new ArrayList<>();
            int count = 0;
            while (rs.next() && count < limit) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    Object val = rs.getObject(i);
                    if (val instanceof byte[] bytes) {
                        row.add(new String(bytes, StandardCharsets.UTF_8));
                    } else {
                        row.add(val);
                    }
                }
                rows.add(row);
                count++;
            }
            return rows;
        });
    }

    private boolean isLobType(String type) {
        if (type == null) return false;
        String upper = type.toUpperCase();
        return upper.contains("LOB") || upper.equals("BLOB") || upper.equals("CLOB") || upper.equals("NCLOB") || upper.equals("RAW") || upper.equals("LONG") || upper.equals("BFILE");
    }
}

package com.bank.migration.validate;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChecksumValidator {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer sourceRenderer;
    private final IdentifierRenderer targetRenderer;

    public ChecksumValidator(
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
                "checksum",
                ValidationStatus.PASS,
                table.name(),
                "No scalar columns to checksum. Skipped LOBs: " + lobCols
            );
        }

        List<String> pkCols = table.keys().stream()
            .filter(k -> "PRIMARY_KEY".equalsIgnoreCase(k.type()))
            .flatMap(k -> k.columns().stream())
            .toList();

        String sourceSelect = buildSelect(table.schema(), table.name(), scalarCols, pkCols, sourceRenderer);
        String targetSelect = buildSelect(targetSchema, table.name(), scalarCols, pkCols, targetRenderer);

        String sourceChecksum = calculateChecksum(sourceJdbc, sourceSelect, scalarCols);
        String targetChecksum = calculateChecksum(targetJdbc, targetSelect, scalarCols);

        boolean pass = sourceChecksum.equals(targetChecksum);
        String details = "source=" + sourceChecksum + " target=" + targetChecksum;
        if (!lobCols.isEmpty()) {
            details += " (Skipped LOB columns: " + lobCols + ")";
        }

        return new ValidationResult(
            "checksum",
            pass ? ValidationStatus.PASS : ValidationStatus.FAIL,
            table.name(),
            details
        );
    }

    private String buildSelect(String schema, String tableName, List<String> columns, List<String> pkCols, IdentifierRenderer renderer) {
        StringBuilder sb = new StringBuilder("select ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(renderer.render(columns.get(i)));
        }
        sb.append(" from ").append(renderer.renderQualifiedName(schema, tableName));
        if (!pkCols.isEmpty()) {
            sb.append(" order by ");
            for (int i = 0; i < pkCols.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(renderer.render(pkCols.get(i)));
            }
        }
        return sb.toString();
    }

    private String calculateChecksum(JdbcTemplate jdbc, String selectSql, List<String> columns) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            jdbc.query(selectSql, rs -> {
                StringBuilder rowSb = new StringBuilder();
                for (int i = 1; i <= columns.size(); i++) {
                    Object val = rs.getObject(i);
                    rowSb.append(val == null ? "NULL" : val.toString()).append("||");
                }
                digest.update(rowSb.toString().getBytes(StandardCharsets.UTF_8));
            });
            byte[] bytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate MD5 checksum for query: " + selectSql, e);
        }
    }

    private boolean isLobType(String type) {
        if (type == null) return false;
        String upper = type.toUpperCase();
        return upper.contains("LOB") || upper.equals("BLOB") || upper.equals("CLOB") || upper.equals("NCLOB") || upper.equals("RAW") || upper.equals("LONG") || upper.equals("BFILE");
    }
}

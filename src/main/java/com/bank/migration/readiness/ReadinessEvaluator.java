package com.bank.migration.readiness;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.types.OracleToGaussTypeMapper;
import com.bank.migration.types.TypeMappingResult;
import com.bank.migration.types.TypeRisk;
import com.bank.migration.types.UnsupportedTypePolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ReadinessEvaluator {
    private final List<ReadinessRule> rules;

    public ReadinessEvaluator(OracleToGaussTypeMapper typeMapper) {
        this.rules = List.of(
            new BfileRule(),
            new VarcharPkRule(),
            new ReservedLimitRule(),
            new ClobRule(),
            new NoPkRule(),
            new OracleSpecificViewPatternRule(),
            new ExcludedForeignKeyRule(),
            new TypeMappingRule(typeMapper),
            new SchemaObjectReadinessRule()
        );
    }

    public ReadinessReport evaluate(MigrationManifest manifest, MigrationProperties properties) {
        List<ReadinessFinding> findings = new ArrayList<>();
        for (ReadinessRule rule : rules) {
            findings.addAll(rule.evaluate(manifest, properties));
        }
        boolean shouldStop = findings.stream().anyMatch(ReadinessFinding::shouldStop);
        return new ReadinessReport(findings, shouldStop);
    }

    private static class BfileRule implements ReadinessRule {
        @Override
        public List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties) {
            List<ReadinessFinding> findings = new ArrayList<>();

            for (var table : manifest.tables()) {
                if (table.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                for (var col : table.columns()) {
                    if (col.oracleType() != null && col.oracleType().equalsIgnoreCase("BFILE")) {
                        findings.add(new ReadinessFinding(
                            "RDN-001",
                            ReadinessSeverity.BLOCKER,
                            "COLUMN",
                            table.name(),
                            col.name(),
                            "Unsupported Oracle BFILE column type detected. GaussDB has no native equivalent.",
                            "Scan manifest table column types for BFILE",
                            "Exclude this table/column or map to CLOB/BLOB if appropriate.",
                            true
                        ));
                    }
                }
            }
            return findings;
        }
    }

    private static class VarcharPkRule implements ReadinessRule {
        @Override
        public List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties) {
            List<ReadinessFinding> findings = new ArrayList<>();
            for (var table : manifest.tables()) {
                if (table.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                var pkCols = table.keys().stream()
                    .filter(k -> "PRIMARY_KEY".equalsIgnoreCase(k.type()))
                    .flatMap(k -> k.columns().stream())
                    .toList();
                
                for (String pkCol : pkCols) {
                    var colOpt = table.columns().stream().filter(c -> c.name().equalsIgnoreCase(pkCol)).findFirst();
                    if (colOpt.isPresent()) {
                        var col = colOpt.get();
                        String type = col.oracleType() != null ? col.oracleType().toUpperCase() : "";
                        if (type.contains("VARCHAR") || type.contains("CHAR")) {
                            findings.add(new ReadinessFinding(
                                "RDN-002",
                                ReadinessSeverity.HIGH,
                                "COLUMN",
                                table.name(),
                                col.name(),
                                "VARCHAR primary key column detected. Non-numeric primary keys can cause bounds or predicate failure during chunking.",
                                "Scan manifest primary key columns for character types",
                                "Implement a ROWID-based query splitter or hash partitioner.",
                                false
                            ));
                        }
                    }
                }
            }
            return findings;
        }
    }

    private static class ReservedLimitRule implements ReadinessRule {
        @Override
        public List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties) {
            List<ReadinessFinding> findings = new ArrayList<>();
            IdentifierMappingPolicy policy = properties != null ? properties.identifierPolicy() : null;
            boolean isRename = policy == IdentifierMappingPolicy.RENAME;
            
            for (var table : manifest.tables()) {
                if (table.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                for (var col : table.columns()) {
                    if (col.name() != null && col.name().equalsIgnoreCase("LIMIT")) {
                        findings.add(new ReadinessFinding(
                            "RDN-003",
                            isRename ? ReadinessSeverity.BLOCKER : ReadinessSeverity.HIGH,
                            "COLUMN",
                            table.name(),
                            col.name(),
                            "Column name matches reserved word LIMIT. Mapping policy is " + (policy != null ? policy : "default (QUOTE)") + ".",
                            "Scan column names for reserved word 'LIMIT'",
                            isRename 
                                ? "Use QUOTE policy to avoid breaking column references, or adapt client applications."
                                : "Ensure client application queries double-quote the identifier.",
                            isRename
                        ));
                    }
                }
            }
            return findings;
        }
    }

    private static class ClobRule implements ReadinessRule {
        @Override
        public List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties) {
            List<ReadinessFinding> findings = new ArrayList<>();
            for (var table : manifest.tables()) {
                if (table.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                for (var col : table.columns()) {
                    if (col.oracleType() != null && (col.oracleType().equalsIgnoreCase("CLOB") || col.oracleType().equalsIgnoreCase("NCLOB"))) {
                        findings.add(new ReadinessFinding(
                            "RDN-004",
                            ReadinessSeverity.MEDIUM,
                            "COLUMN",
                            table.name(),
                            col.name(),
                            "LOB column (CLOB) detected. Passing LOBs directly can cause serialization exceptions.",
                            "Scan manifest columns for CLOB/NCLOB type",
                            "Ensure LOB streaming is enabled or read LOB column as bytes/streams.",
                            false
                        ));
                    }
                }
            }
            return findings;
        }
    }

    private static class NoPkRule implements ReadinessRule {
        @Override
        public List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties) {
            List<ReadinessFinding> findings = new ArrayList<>();
            for (var table : manifest.tables()) {
                if (table.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                boolean hasPk = table.keys().stream()
                    .anyMatch(k -> "PRIMARY_KEY".equalsIgnoreCase(k.type()));
                if (!hasPk) {
                    findings.add(new ReadinessFinding(
                        "RDN-005",
                        ReadinessSeverity.MEDIUM,
                        "TABLE",
                        table.name(),
                        null,
                        "Table has no primary key defined.",
                        "Check manifest table keys for primary key",
                        "Create primary key on target, or rely on ROWID fallback chunking strategy.",
                        false
                    ));
                }
            }
            return findings;
        }
    }

    private static class OracleSpecificViewPatternRule implements ReadinessRule {
        @Override
        public List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties) {
            List<ReadinessFinding> findings = new ArrayList<>();
            for (var view : manifest.views()) {
                if (view.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                String sql = view.sql();
                if (sql == null) {
                    continue;
                }
                String sqlLower = sql.toLowerCase();
                String sqlNormalized = sqlLower.replaceAll("\\s+", " ");

                if (sqlLower.contains("sys_context")) {
                    findings.add(createFinding(view.name(), "sys_context"));
                }
                if (sqlLower.contains("connect by")) {
                    findings.add(createFinding(view.name(), "connect by"));
                }
                if (sqlNormalized.contains("from dual") || sqlNormalized.contains("join dual")) {
                    findings.add(createFinding(view.name(), "from dual"));
                }
                if (sql.contains("(+)")) {
                    findings.add(createFinding(view.name(), "(+)"));
                }
                if (sqlLower.contains("nchar_cs")) {
                    findings.add(createFinding(view.name(), "nchar_cs"));
                }
            }
            return findings;
        }

        private ReadinessFinding createFinding(String viewName, String pattern) {
            return new ReadinessFinding(
                "RDN-007",
                ReadinessSeverity.HIGH,
                "VIEW",
                viewName,
                null,
                "Oracle-specific SQL pattern '" + pattern + "' detected in view definition.",
                "Scan manifest view SQL definitions for Oracle-specific syntax",
                "Rewrite view definition using GaussDB standard SQL features (e.g. standard outer joins, CTEs, etc.).",
                false
            );
        }
    }

    private static class ExcludedForeignKeyRule implements ReadinessRule {
        @Override
        public List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties) {
            List<ReadinessFinding> findings = new ArrayList<>();
            java.util.Set<String> excludedTableNames = manifest.tables().stream()
                .filter(t -> t.status() == ObjectStatus.EXCLUDED)
                .map(t -> t.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

            for (var table : manifest.tables()) {
                if (table.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                for (var key : table.keys()) {
                    if ("FOREIGN_KEY".equalsIgnoreCase(key.type()) && key.referencedTable() != null) {
                        if (excludedTableNames.contains(key.referencedTable().toLowerCase(Locale.ROOT))) {
                            findings.add(new ReadinessFinding(
                                "RDN-008",
                                ReadinessSeverity.BLOCKER,
                                "FOREIGN_KEY",
                                key.name(),
                                null,
                                "Non-excluded foreign key '" + key.name() + "' on table '" + table.name() + "' references excluded table '" + key.referencedTable() + "'.",
                                "Scan non-excluded foreign keys for references to excluded tables",
                                "Exclude table '" + table.name() + "', drop foreign key '" + key.name() + "', or include the referenced table '" + key.referencedTable() + "' in the migration.",
                                true
                            ));
                        }
                    }
                }
            }
            return findings;
        }
    }

    private static class TypeMappingRule implements ReadinessRule {
        private final OracleToGaussTypeMapper typeMapper;

        public TypeMappingRule(OracleToGaussTypeMapper typeMapper) {
            this.typeMapper = typeMapper;
        }

        @Override
        public List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties) {
            List<ReadinessFinding> findings = new ArrayList<>();
            UnsupportedTypePolicy policy = properties != null && properties.unsupportedTypePolicy() != null
                ? properties.unsupportedTypePolicy()
                : UnsupportedTypePolicy.FAIL;

            OracleToGaussTypeMapper currentMapper = (properties != null)
                ? new OracleToGaussTypeMapper(policy)
                : this.typeMapper;

            for (var table : manifest.tables()) {
                if (table.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                for (var col : table.columns()) {
                    String oracleType = col.oracleType();
                    if (oracleType != null) {
                        boolean isClobOrNclob = oracleType.equalsIgnoreCase("CLOB") || oracleType.equalsIgnoreCase("NCLOB");
                        boolean isBfile = oracleType.equalsIgnoreCase("BFILE");
                        if (isClobOrNclob || isBfile) {
                            continue;
                        }
                    }
                    var mapped = currentMapper.map(col);
                    if (mapped.risk() == TypeRisk.BLOCKED) {
                        findings.add(new ReadinessFinding(
                            "RDN-009",
                            ReadinessSeverity.BLOCKER,
                            "COLUMN",
                            table.name(),
                            col.name(),
                            "Blocked column type: " + col.oracleType() + ". " + String.join(", ", mapped.notes()),
                            "Scan column types for BLOCKED mappings",
                            "Exclude this column or table, or configure unsupported-type-policy fallback.",
                            true
                        ));
                    } else if (mapped.risk() == TypeRisk.REVIEW) {
                        boolean hasDataLossRisk = mapped.notes().stream().anyMatch(n -> n.contains("fell back to text"));
                        ReadinessSeverity severity = hasDataLossRisk ? ReadinessSeverity.HIGH : ReadinessSeverity.MEDIUM;

                        findings.add(new ReadinessFinding(
                            "RDN-010",
                            severity,
                            "COLUMN",
                            table.name(),
                            col.name(),
                            "Risky column type: " + col.oracleType() + ". " + String.join(", ", mapped.notes()),
                            "Scan column types for REVIEW mappings",
                            "Review target type mappings and ensure custom copy converters are in place.",
                            false
                        ));
                    }
                }
            }
            return findings;
        }
    }

    private static class SchemaObjectReadinessRule implements ReadinessRule {
        @Override
        public List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties) {
            List<ReadinessFinding> findings = new ArrayList<>();
            if (manifest.schemaObjects() == null) {
                return findings;
            }
            for (var obj : manifest.schemaObjects()) {
                if (obj.status() == ObjectStatus.EXCLUDED) {
                    continue;
                }
                String code;
                ReadinessSeverity severity;
                String desc;
                String method = "Scan manifest schemaObjects";
                String mitigation;

                switch (obj.type()) {
                    case SEQUENCE:
                        code = "RDN-011";
                        severity = ReadinessSeverity.MEDIUM;
                        desc = "Sequence '" + obj.name() + "' detected. Sequences are not migrated automatically.";
                        mitigation = "Recreate on target database manually or run app sequences setup.";
                        break;
                    case SYNONYM:
                        code = "RDN-012";
                        severity = ReadinessSeverity.MEDIUM;
                        desc = "Synonym '" + obj.name() + "' detected. Out of scope for automated migration.";
                        mitigation = "Review if synonym is needed on target GaussDB and recreate if needed.";
                        break;
                    case MATERIALIZED_VIEW:
                        code = "RDN-013";
                        severity = ReadinessSeverity.MEDIUM;
                        desc = "Materialized view '" + obj.name() + "' detected. Materialized views are not migrated.";
                        mitigation = "Review materialized view logic and rewrite on target database manually.";
                        break;
                    case TRIGGER:
                        code = "RDN-014";
                        severity = ReadinessSeverity.MEDIUM;
                        desc = "Trigger '" + obj.name() + "' detected. Triggers are out of scope.";
                        mitigation = "Port trigger logic to target database triggers or application logic.";
                        break;
                    case PROCEDURE:
                        code = "RDN-015";
                        severity = ReadinessSeverity.MEDIUM;
                        desc = "Procedure '" + obj.name() + "' detected. PL/SQL procedures are not migrated.";
                        mitigation = "Rewrite procedure logic using openGauss PL/pgSQL manually.";
                        break;
                    case FUNCTION:
                        code = "RDN-016";
                        severity = ReadinessSeverity.MEDIUM;
                        desc = "Function '" + obj.name() + "' detected. PL/SQL functions are not migrated.";
                        mitigation = "Rewrite function logic using openGauss PL/pgSQL manually.";
                        break;
                    case PACKAGE:
                        code = "RDN-017";
                        severity = ReadinessSeverity.MEDIUM;
                        desc = "Package '" + obj.name() + "' detected. Packages are not supported on target database.";
                        mitigation = "Port package functions/procedures to standard procedures/functions.";
                        break;
                    case TYPE:
                        code = "RDN-018";
                        severity = ReadinessSeverity.MEDIUM;
                        desc = "Custom Type '" + obj.name() + "' detected. Types are out of scope.";
                        mitigation = "Map custom types to standard postgres types or recreate manually.";
                        break;
                    case CHECK_CONSTRAINT:
                        code = "RDN-019";
                        severity = ReadinessSeverity.HIGH;
                        desc = "Check constraint '" + obj.name() + "' detected. Check constraints are not migrated.";
                        mitigation = "Ensure target tables have corresponding CHECK constraints manually recreated.";
                        break;
                    case FUNCTION_BASED_INDEX:
                        code = "RDN-020";
                        severity = ReadinessSeverity.HIGH;
                        desc = "Function-based index '" + obj.name() + "' detected. Manual recreation needed.";
                        mitigation = "Rewrite function-based index expression on target table manually.";
                        break;
                    case BITMAP_INDEX:
                        code = "RDN-021";
                        severity = ReadinessSeverity.MEDIUM;
                        desc = "Bitmap index '" + obj.name() + "' detected. Target compatibility review needed.";
                        mitigation = "GaussDB does not support bitmap indexes; map to B-Tree index or exclude.";
                        break;
                    default:
                        continue;
                }

                findings.add(new ReadinessFinding(
                    code,
                    severity,
                    obj.type().name(),
                    obj.name(),
                    null,
                    desc,
                    method,
                    mitigation,
                    false
                ));
            }
            return findings;
        }
    }
}

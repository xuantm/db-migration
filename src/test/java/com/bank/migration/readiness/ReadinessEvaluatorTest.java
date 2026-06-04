package com.bank.migration.readiness;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.domain.ViewMetadata;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.types.OracleToGaussTypeMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReadinessEvaluatorTest {
    private final ReadinessEvaluator evaluator = new ReadinessEvaluator(new OracleToGaussTypeMapper());

    @Test
    void testBfileColumnBlocker() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(new TableMetadata(
                "BANK_CORE",
                "MY_TABLE",
                ObjectStatus.READY,
                List.of(
                    new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                    new ColumnMetadata("BFILE_COL", "BFILE", null, null, true, null)
                ),
                List.of(new KeyMetadata("PK_MY_TABLE", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of()
            )),
            List.of()
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isTrue();
        assertThat(report.findings()).hasSize(1);
        ReadinessFinding finding = report.findings().getFirst();
        assertThat(finding.code()).isEqualTo("RDN-001");
        assertThat(finding.severity()).isEqualTo(ReadinessSeverity.BLOCKER);
        assertThat(finding.shouldStop()).isTrue();
        assertThat(finding.columnName()).isEqualTo("BFILE_COL");
    }

    @Test
    void testVarcharPrimaryKeyHighRisk() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(new TableMetadata(
                "BANK_CORE",
                "MY_TABLE",
                ObjectStatus.READY,
                List.of(
                    new ColumnMetadata("ID", "VARCHAR2", 32, null, false, null)
                ),
                List.of(new KeyMetadata("PK_MY_TABLE", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of()
            )),
            List.of()
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isFalse();
        assertThat(report.findings()).hasSize(1);
        ReadinessFinding finding = report.findings().getFirst();
        assertThat(finding.code()).isEqualTo("RDN-002");
        assertThat(finding.severity()).isEqualTo(ReadinessSeverity.HIGH);
        assertThat(finding.shouldStop()).isFalse();
        assertThat(finding.columnName()).isEqualTo("ID");
    }

    @Test
    void testReservedLimitColumnUnderQuotePolicy() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(new TableMetadata(
                "BANK_CORE",
                "MY_TABLE",
                ObjectStatus.READY,
                List.of(
                    new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                    new ColumnMetadata("LIMIT", "NUMBER", 18, 0, false, null)
                ),
                List.of(new KeyMetadata("PK_MY_TABLE", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of()
            )),
            List.of()
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isFalse();
        assertThat(report.findings()).hasSize(1);
        ReadinessFinding finding = report.findings().getFirst();
        assertThat(finding.code()).isEqualTo("RDN-003");
        assertThat(finding.severity()).isEqualTo(ReadinessSeverity.HIGH);
        assertThat(finding.shouldStop()).isFalse();
        assertThat(finding.columnName()).isEqualTo("LIMIT");
    }

    @Test
    void testReservedLimitColumnUnderRenamePolicy() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(new TableMetadata(
                "BANK_CORE",
                "MY_TABLE",
                ObjectStatus.READY,
                List.of(
                    new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                    new ColumnMetadata("LIMIT", "NUMBER", 18, 0, false, null)
                ),
                List.of(new KeyMetadata("PK_MY_TABLE", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of()
            )),
            List.of()
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.RENAME));
        assertThat(report.shouldStop()).isTrue();
        assertThat(report.findings()).hasSize(1);
        ReadinessFinding finding = report.findings().getFirst();
        assertThat(finding.code()).isEqualTo("RDN-003");
        assertThat(finding.severity()).isEqualTo(ReadinessSeverity.BLOCKER);
        assertThat(finding.shouldStop()).isTrue();
        assertThat(finding.columnName()).isEqualTo("LIMIT");
    }

    @Test
    void testClobColumnMediumRisk() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(new TableMetadata(
                "BANK_CORE",
                "MY_TABLE",
                ObjectStatus.READY,
                List.of(
                    new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                    new ColumnMetadata("DATA", "CLOB", null, null, true, null)
                ),
                List.of(new KeyMetadata("PK_MY_TABLE", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of()
            )),
            List.of()
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isFalse();
        assertThat(report.findings()).hasSize(1);
        ReadinessFinding finding = report.findings().getFirst();
        assertThat(finding.code()).isEqualTo("RDN-004");
        assertThat(finding.severity()).isEqualTo(ReadinessSeverity.MEDIUM);
        assertThat(finding.shouldStop()).isFalse();
        assertThat(finding.columnName()).isEqualTo("DATA");
    }

    @Test
    void testNoPrimaryKeyMediumRisk() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(new TableMetadata(
                "BANK_CORE",
                "MY_TABLE",
                ObjectStatus.READY,
                List.of(
                    new ColumnMetadata("ID", "NUMBER", 18, 0, false, null)
                ),
                List.of(),
                List.of()
            )),
            List.of()
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isFalse();
        assertThat(report.findings()).hasSize(1);
        ReadinessFinding finding = report.findings().getFirst();
        assertThat(finding.code()).isEqualTo("RDN-005");
        assertThat(finding.severity()).isEqualTo(ReadinessSeverity.MEDIUM);
        assertThat(finding.shouldStop()).isFalse();
        assertThat(finding.objectName()).isEqualTo("MY_TABLE");
    }

    @Test
    void testFunctionBasedIndexRule() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(),
            List.of(),
            List.of(new com.bank.migration.domain.SchemaObjectMetadata(
                "BANK_CORE",
                "IDX_NAME_UPPER",
                com.bank.migration.domain.SchemaObjectType.FUNCTION_BASED_INDEX,
                ObjectStatus.NEEDS_REVIEW,
                List.of("Function-based index on table MY_TABLE")
            ))
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isFalse();
        
        List<ReadinessFinding> indexFindings = report.findings().stream()
            .filter(f -> "FUNCTION_BASED_INDEX".equals(f.objectType()))
            .toList();
        
        assertThat(indexFindings).hasSize(1);
        ReadinessFinding finding = indexFindings.getFirst();
        assertThat(finding.code()).isEqualTo("RDN-020");
        assertThat(finding.severity()).isEqualTo(ReadinessSeverity.HIGH);
        assertThat(finding.objectName()).isEqualTo("IDX_NAME_UPPER");
        assertThat(finding.shouldStop()).isFalse();
    }

    @Test
    void testOracleSpecificViewPatternRule() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(),
            List.of(
                new ViewMetadata(
                    "BANK_CORE",
                    "VW_SYS_CONTEXT",
                    ObjectStatus.READY,
                    "SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') FROM DUAL",
                    List.of(),
                    List.of()
                ),
                new ViewMetadata(
                    "BANK_CORE",
                    "VW_OUTER_JOIN",
                    ObjectStatus.READY,
                    "SELECT a.id, b.name FROM table_a a, table_b b WHERE a.id = b.id(+)",
                    List.of(),
                    List.of()
                ),
                new ViewMetadata(
                    "BANK_CORE",
                    "VW_CONNECT_BY",
                    ObjectStatus.READY,
                    "SELECT id FROM hierarchy CONNECT BY PRIOR id = parent_id",
                    List.of(),
                    List.of()
                ),
                new ViewMetadata(
                    "BANK_CORE",
                    "VW_NCHAR_CS",
                    ObjectStatus.READY,
                    "SELECT NCHAR_CS(col) FROM t",
                    List.of(),
                    List.of()
                )
            )
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isFalse();
        
        List<ReadinessFinding> viewFindings = report.findings().stream()
            .filter(f -> "VIEW".equals(f.objectType()))
            .toList();
        
        assertThat(viewFindings).hasSize(5);
        
        assertThat(viewFindings).anyMatch(f -> "VW_SYS_CONTEXT".equals(f.objectName()) && f.description().contains("sys_context"));
        assertThat(viewFindings).anyMatch(f -> "VW_SYS_CONTEXT".equals(f.objectName()) && f.description().contains("from dual"));
        assertThat(viewFindings).anyMatch(f -> "VW_OUTER_JOIN".equals(f.objectName()) && f.description().contains("(+)"));
        assertThat(viewFindings).anyMatch(f -> "VW_CONNECT_BY".equals(f.objectName()) && f.description().contains("connect by"));
        assertThat(viewFindings).anyMatch(f -> "VW_NCHAR_CS".equals(f.objectName()) && f.description().contains("nchar_cs"));
    }

    @Test
    void testExcludedForeignKeyBlocker() {
        TableMetadata excludedTable = new TableMetadata(
            "BANK_CORE",
            "EXCLUDED_TABLE",
            ObjectStatus.EXCLUDED,
            List.of(new ColumnMetadata("ID", "NUMBER", 18, 0, false, null)),
            List.of(new KeyMetadata("PK_EXCLUDED", "PRIMARY_KEY", List.of("ID"), null, null)),
            List.of()
        );
        TableMetadata activeTable = new TableMetadata(
            "BANK_CORE",
            "ACTIVE_TABLE",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                new ColumnMetadata("REF_ID", "NUMBER", 18, 0, false, null)
            ),
            List.of(
                new KeyMetadata("PK_ACTIVE", "PRIMARY_KEY", List.of("ID"), null, null),
                new KeyMetadata(
                    "FK_ACTIVE_EXCLUDED",
                    "FOREIGN_KEY",
                    List.of("REF_ID"),
                    "EXCLUDED_TABLE",
                    List.of("ID")
                )
            ),
            List.of()
        );

        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(excludedTable, activeTable),
            List.of()
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isTrue();
        assertThat(report.findings()).hasSize(1);
        
        ReadinessFinding finding = report.findings().getFirst();
        assertThat(finding.code()).isEqualTo("RDN-008");
        assertThat(finding.severity()).isEqualTo(ReadinessSeverity.BLOCKER);
        assertThat(finding.shouldStop()).isTrue();
        assertThat(finding.objectName()).isEqualTo("FK_ACTIVE_EXCLUDED");
        assertThat(finding.description()).contains("references excluded table");
    }

    @Test
    void testExcludedTableBlockersAreSkipped() {
        TableMetadata excludedTable = new TableMetadata(
            "BANK_CORE",
            "EXCLUDED_TABLE",
            ObjectStatus.EXCLUDED,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                new ColumnMetadata("BFILE_COL", "BFILE", null, null, true, null)
            ),
            List.of(new KeyMetadata("PK_EXCLUDED", "PRIMARY_KEY", List.of("ID"), null, null)),
            List.of(new IndexMetadata("IDX_NAME_UPPER", false, List.of("UPPER(NAME)")))
        );

        ViewMetadata excludedView = new ViewMetadata(
            "BANK_CORE",
            "EXCLUDED_VIEW",
            ObjectStatus.EXCLUDED,
            "SELECT SYS_CONTEXT('USERENV', 'SESSION_USER') FROM DUAL",
            List.of(),
            List.of()
        );

        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(excludedTable),
            List.of(excludedView)
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isFalse();
        assertThat(report.findings()).isEmpty();
    }

    @Test
    void testSchemaObjectReadinessFindings() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(),
            List.of(),
            List.of(
                new com.bank.migration.domain.SchemaObjectMetadata(
                    "BANK_CORE",
                    "SEQ_TEST",
                    com.bank.migration.domain.SchemaObjectType.SEQUENCE,
                    ObjectStatus.READY,
                    List.of()
                ),
                new com.bank.migration.domain.SchemaObjectMetadata(
                    "BANK_CORE",
                    "CK_TEST",
                    com.bank.migration.domain.SchemaObjectType.CHECK_CONSTRAINT,
                    ObjectStatus.READY,
                    List.of()
                )
            )
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isFalse();
        assertThat(report.findings()).hasSize(2);
        assertThat(report.findings()).anyMatch(f -> "RDN-011".equals(f.code()) && f.severity() == ReadinessSeverity.MEDIUM);
        assertThat(report.findings()).anyMatch(f -> "RDN-019".equals(f.code()) && f.severity() == ReadinessSeverity.HIGH);
    }

    @Test
    void testExcludedSchemaObjectReadinessFindings() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(),
            List.of(),
            List.of(
                new com.bank.migration.domain.SchemaObjectMetadata(
                    "BANK_CORE",
                    "SEQ_TEST",
                    com.bank.migration.domain.SchemaObjectType.SEQUENCE,
                    ObjectStatus.EXCLUDED,
                    List.of(),
                    "Excluded by configuration"
                ),
                new com.bank.migration.domain.SchemaObjectMetadata(
                    "BANK_CORE",
                    "CK_TEST",
                    com.bank.migration.domain.SchemaObjectType.CHECK_CONSTRAINT,
                    ObjectStatus.READY,
                    List.of()
                )
            )
        );

        ReadinessReport report = evaluator.evaluate(manifest, properties(IdentifierMappingPolicy.QUOTE));
        assertThat(report.shouldStop()).isFalse();
        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings()).anyMatch(f -> "RDN-019".equals(f.code()) && f.severity() == ReadinessSeverity.HIGH);
        assertThat(report.findings()).noneMatch(f -> "RDN-011".equals(f.code()));
    }

    @Test
    void testBfileColumnFallbackToTextStops() {
        MigrationManifest manifest = new MigrationManifest(
            "run-1",
            "BANK_CORE",
            List.of(new TableMetadata(
                "BANK_CORE",
                "MY_TABLE",
                ObjectStatus.READY,
                List.of(
                    new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                    new ColumnMetadata("BFILE_COL", "BFILE", null, null, true, null)
                ),
                List.of(new KeyMetadata("PK_MY_TABLE", "PRIMARY_KEY", List.of("ID"), null, null)),
                List.of()
            )),
            List.of()
        );

        ReadinessReport report = evaluator.evaluate(
            manifest,
            properties(IdentifierMappingPolicy.QUOTE, com.bank.migration.types.UnsupportedTypePolicy.FALLBACK_TO_TEXT)
        );
        assertThat(report.shouldStop()).isTrue();
        assertThat(report.findings()).hasSize(1);
        ReadinessFinding finding = report.findings().getFirst();
        assertThat(finding.code()).isEqualTo("RDN-001");
        assertThat(finding.severity()).isEqualTo(ReadinessSeverity.BLOCKER);
        assertThat(finding.shouldStop()).isTrue();
        assertThat(finding.columnName()).isEqualTo("BFILE_COL");
        assertThat(finding.description()).contains("Unsupported Oracle BFILE column type detected");
    }

    @Test
    void testOracleSpecificUnsupportedTypesFallbackToTextStops() {
        for (String type : List.of("XMLTYPE", "SDO_GEOMETRY", "LONG")) {
            MigrationManifest manifest = new MigrationManifest(
                "run-1",
                "BANK_CORE",
                List.of(new TableMetadata(
                    "BANK_CORE",
                    "MY_TABLE",
                    ObjectStatus.READY,
                    List.of(
                        new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                        new ColumnMetadata("COL_" + type, type, null, null, true, null)
                    ),
                    List.of(new KeyMetadata("PK_MY_TABLE", "PRIMARY_KEY", List.of("ID"), null, null)),
                    List.of()
                )),
                List.of()
            );

            ReadinessReport report = evaluator.evaluate(
                manifest,
                properties(IdentifierMappingPolicy.QUOTE, com.bank.migration.types.UnsupportedTypePolicy.FALLBACK_TO_TEXT)
            );
            assertThat(report.shouldStop()).isTrue();
            assertThat(report.findings()).hasSize(1);
            ReadinessFinding finding = report.findings().getFirst();
            assertThat(finding.code()).isEqualTo("RDN-009");
            assertThat(finding.severity()).isEqualTo(ReadinessSeverity.BLOCKER);
            assertThat(finding.shouldStop()).isTrue();
            assertThat(finding.columnName()).isEqualTo("COL_" + type);
            assertThat(finding.description()).contains("Blocked column type: " + type);
        }
    }

    private MigrationProperties properties(IdentifierMappingPolicy policy) {
        return properties(policy, com.bank.migration.types.UnsupportedTypePolicy.FAIL);
    }

    private MigrationProperties properties(IdentifierMappingPolicy policy, com.bank.migration.types.UnsupportedTypePolicy typePolicy) {
        return new MigrationProperties(
            new MigrationProperties.Database("src", "u", "p", "driver", "BANK_CORE"),
            new MigrationProperties.Database("dst", "u", "p", "driver", "bank_core"),
            true,
            new MigrationProperties.Batch(5000, 5000, 2),
            new MigrationProperties.Reports("build/reports"),
            policy,
            null,
            null,
            typePolicy
        );
    }
}

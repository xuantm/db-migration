package com.bank.migration.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.audit.ErrorRecord;
import com.bank.migration.validate.ValidationResult;
import com.bank.migration.validate.ValidationStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportWriterTest {
    @TempDir Path tempDir;

    @Test
    void writesJsonCsvAndHtmlReports() throws Exception {
        ReportWriter writer = new ReportWriter();
        MigrationReport report = new MigrationReport(
            "run-001",
            "PASS",
            Instant.parse("2026-06-02T00:00:00Z"),
            Instant.parse("2026-06-02T00:10:00Z"),
            List.of(new ValidationResult("row-count", ValidationStatus.PASS, "ACCOUNT", "source=10 target=10")),
            List.of()
        );

        writer.write(report, tempDir);

        assertThat(Files.readString(tempDir.resolve("run-001-report.json"))).contains("\"status\" : \"PASS\"");
        assertThat(Files.readString(tempDir.resolve("run-001-report.csv"))).contains("row-count,PASS,ACCOUNT");
        assertThat(Files.readString(tempDir.resolve("run-001-report.html"))).contains("<h1>Migration Report run-001</h1>");
    }

    @Test
    void escapesCsvAndHtmlReportValues() throws Exception {
        ReportWriter writer = new ReportWriter();
        MigrationReport report = new MigrationReport(
            "run-002",
            "FAIL",
            Instant.parse("2026-06-02T00:00:00Z"),
            Instant.parse("2026-06-02T00:10:00Z"),
            List.of(new ValidationResult("row-count", ValidationStatus.FAIL, "ACCOUNT", "bad <value>, needs \"review\"")),
            List.of(new ErrorRecord(
                "err-001",
                "run-002",
                "data-load",
                "TABLE",
                "ACCOUNT",
                "ACCOUNT-000001",
                "ID >= 1",
                "ORA-00001",
                "bad <chunk>, needs \"review\"",
                "RETRY_OR_MANUAL_REVIEW",
                Instant.parse("2026-06-02T00:01:00Z")
            ))
        );

        writer.write(report, tempDir);

        assertThat(Files.readString(tempDir.resolve("run-002-report.csv")))
            .contains("\"bad <value>, needs \"\"review\"\"\"")
            .contains("ERROR,data-load,RETRY_OR_MANUAL_REVIEW,ACCOUNT,ACCOUNT-000001,ORA-00001,ID >= 1,\"bad <chunk>, needs \"\"review\"\"\"");
        assertThat(Files.readString(tempDir.resolve("run-002-report.html")))
            .contains("bad &lt;value&gt;, needs &quot;review&quot;")
            .contains("ORA-00001")
            .contains("ID &gt;= 1")
            .contains("bad &lt;chunk&gt;, needs &quot;review&quot;");
    }

    @Test
    void writesReadinessFindingsReports() throws Exception {
        ReportWriter writer = new ReportWriter();
        MigrationReport report = new MigrationReport(
            "run-003",
            "FAIL",
            Instant.parse("2026-06-02T00:00:00Z"),
            Instant.parse("2026-06-02T00:10:00Z"),
            List.of(),
            List.of(),
            List.of(new com.bank.migration.readiness.ReadinessFinding(
                "RDN-001",
                com.bank.migration.readiness.ReadinessSeverity.BLOCKER,
                "COLUMN",
                "ACCOUNT",
                "LIMIT",
                "Column name matches reserved word LIMIT",
                "Scan column names",
                "Quote or Rename",
                true
            ))
        );

        writer.write(report, tempDir);

        String json = Files.readString(tempDir.resolve("run-003-report.json"));
        String csv = Files.readString(tempDir.resolve("run-003-report.csv"));
        String readinessCsv = Files.readString(tempDir.resolve("run-003-readiness.csv"));
        String html = Files.readString(tempDir.resolve("run-003-report.html"));

        assertThat(json).contains("\"code\" : \"RDN-001\"").contains("\"severity\" : \"BLOCKER\"");
        assertThat(csv).contains("READINESS,RDN-001,BLOCKER,ACCOUNT");
        assertThat(readinessCsv).contains("code,severity,object_type,object_name")
            .contains("RDN-001,BLOCKER,COLUMN,ACCOUNT,LIMIT,Column name matches reserved word LIMIT");
        assertThat(html).contains("<h2>Readiness Findings</h2>")
            .contains("RDN-001")
            .contains("BLOCKER")
            .contains("Quote or Rename");
    }

    @Test
    void writesExcludedObjectDecisionsReports() throws Exception {
        ReportWriter writer = new ReportWriter();
        MigrationReport report = new MigrationReport(
            "run-004",
            "PASS",
            Instant.parse("2026-06-02T00:00:00Z"),
            Instant.parse("2026-06-02T00:10:00Z"),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new ExcludedObjectDecision("TABLE", "EBA_PROJECTS", "EXCLUDED", "Excluded by configuration"),
                new ExcludedObjectDecision("VIEW", "V_PROJECT_DETAILS", "NEEDS_REVIEW", "References excluded table(s): EBA_PROJECTS"),
                new ExcludedObjectDecision("SEQUENCE", "SEQ_TEST", "EXCLUDED", "Excluded by configuration")
            )
        );

        writer.write(report, tempDir);

        String json = Files.readString(tempDir.resolve("run-004-report.json"));
        String csv = Files.readString(tempDir.resolve("run-004-report.csv"));
        String exclusionsCsv = Files.readString(tempDir.resolve("run-004-exclusions.csv"));
        String html = Files.readString(tempDir.resolve("run-004-report.html"));

        assertThat(json)
            .contains("\"objectType\" : \"TABLE\"")
            .contains("\"objectName\" : \"EBA_PROJECTS\"")
            .contains("\"status\" : \"EXCLUDED\"")
            .contains("\"reason\" : \"Excluded by configuration\"")
            .contains("\"objectType\" : \"VIEW\"")
            .contains("\"objectName\" : \"V_PROJECT_DETAILS\"")
            .contains("\"status\" : \"NEEDS_REVIEW\"")
            .contains("\"reason\" : \"References excluded table(s): EBA_PROJECTS\"")
            .contains("\"objectType\" : \"SEQUENCE\"")
            .contains("\"objectName\" : \"SEQ_TEST\"")
            .contains("\"status\" : \"EXCLUDED\"")
            .contains("\"reason\" : \"Excluded by configuration\"");

        assertThat(csv)
            .contains("EXCLUSION,EBA_PROJECTS,EXCLUDED,TABLE,,,,Excluded by configuration")
            .contains("EXCLUSION,V_PROJECT_DETAILS,NEEDS_REVIEW,VIEW,,,,References excluded table(s): EBA_PROJECTS")
            .contains("EXCLUSION,SEQ_TEST,EXCLUDED,SEQUENCE,,,,Excluded by configuration");

        assertThat(exclusionsCsv)
            .contains("object_type,object_name,status,reason")
            .contains("TABLE,EBA_PROJECTS,EXCLUDED,Excluded by configuration")
            .contains("VIEW,V_PROJECT_DETAILS,NEEDS_REVIEW,References excluded table(s): EBA_PROJECTS")
            .contains("SEQUENCE,SEQ_TEST,EXCLUDED,Excluded by configuration");

        assertThat(html)
            .contains("<h2>Excluded Objects & Decisions</h2>")
            .contains("TABLE")
            .contains("EBA_PROJECTS")
            .contains("EXCLUDED")
            .contains("Excluded by configuration")
            .contains("VIEW")
            .contains("V_PROJECT_DETAILS")
            .contains("NEEDS_REVIEW")
            .contains("References excluded table(s): EBA_PROJECTS")
            .contains("SEQUENCE")
            .contains("SEQ_TEST")
            .contains("EXCLUDED")
            .contains("Excluded by configuration");
    }

    @Test
    void writesChunkStrategyReports() throws Exception {
        ReportWriter writer = new ReportWriter();
        MigrationReport report = new MigrationReport(
            "run-005",
            "PASS",
            Instant.parse("2026-06-02T00:00:00Z"),
            Instant.parse("2026-06-02T00:10:00Z"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                new ChunkStrategyRecord("TABLE_A", "TABLE_A-000001", "NUMERIC_PRIMARY_KEY", "ID", "ID >= 1 and ID <= 10"),
                new ChunkStrategyRecord("TABLE_B", "TABLE_B-000001", "ROW_NUMBER", "PK1, PK2", "row_num >= 1 and row_num <= 10"),
                new ChunkStrategyRecord("TABLE_C", null, "ROWID", null, null)
            )
        );

        writer.write(report, tempDir);

        String json = Files.readString(tempDir.resolve("run-005-report.json"));
        String strategiesCsv = Files.readString(tempDir.resolve("run-005-strategies.csv"));
        String html = Files.readString(tempDir.resolve("run-005-report.html"));

        assertThat(json)
            .contains("\"tableName\" : \"TABLE_A\"")
            .contains("\"strategy\" : \"NUMERIC_PRIMARY_KEY\"")
            .contains("\"columnName\" : \"ID\"")
            .contains("\"whereClause\" : \"ID >= 1 and ID <= 10\"")
            .contains("\"tableName\" : \"TABLE_B\"")
            .contains("\"strategy\" : \"ROW_NUMBER\"")
            .contains("\"tableName\" : \"TABLE_C\"")
            .contains("\"strategy\" : \"ROWID\"");

        assertThat(strategiesCsv)
            .contains("table_name,chunk_id,strategy,column_name,where_clause")
            .contains("TABLE_A,TABLE_A-000001,NUMERIC_PRIMARY_KEY,ID,ID >= 1 and ID <= 10")
            .contains("TABLE_B,TABLE_B-000001,ROW_NUMBER,\"PK1, PK2\",row_num >= 1 and row_num <= 10")
            .contains("TABLE_C,,ROWID,,");

        assertThat(html)
            .contains("<h2>Migration Strategies</h2>")
            .contains("TABLE_A")
            .contains("NUMERIC_PRIMARY_KEY")
            .contains("ID &gt;= 1 and ID &lt;= 10")
            .contains("TABLE_B")
            .contains("ROW_NUMBER")
            .contains("TABLE_C")
            .contains("ROWID");
    }

    @Test
    void writesMigrationModeAndSkippedPhases() throws Exception {
        MigrationReport report = new MigrationReport(
            "run-123",
            "PASS",
            "DATA_ONLY",
            List.of("ddl-application", "constraints-and-indexes", "view-application"),
            Instant.parse("2026-06-04T00:00:00Z"),
            Instant.parse("2026-06-04T00:01:00Z"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );

        new ReportWriter().write(report, tempDir);

        String json = Files.readString(tempDir.resolve("run-123-report.json"));
        String html = Files.readString(tempDir.resolve("run-123-report.html"));
        assertThat(json).contains("\"mode\" : \"DATA_ONLY\"");
        assertThat(json).contains("ddl-application");
        assertThat(html).contains("Mode: DATA_ONLY");
        assertThat(html).contains("Skipped Phases");
    }
}

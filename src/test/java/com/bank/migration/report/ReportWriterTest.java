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
}

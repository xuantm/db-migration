package com.bank.migration.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class ReportWriter {
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT);

    public void write(MigrationReport report, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve(report.runId() + "-report.json"), mapper.writeValueAsString(report));
        Files.writeString(outputDir.resolve(report.runId() + "-report.csv"), csv(report));
        Files.writeString(outputDir.resolve(report.runId() + "-readiness.csv"), readinessCsv(report));
        Files.writeString(outputDir.resolve(report.runId() + "-exclusions.csv"), exclusionsCsv(report));
        Files.writeString(outputDir.resolve(report.runId() + "-strategies.csv"), strategiesCsv(report));
        Files.writeString(outputDir.resolve(report.runId() + "-report.html"), html(report));
    }

    private String csv(MigrationReport report) {
        StringBuilder builder = new StringBuilder("record_type,name,status,object,chunk,database_code,sql_text,message\n");
        report.validations().forEach(result -> builder
            .append("VALIDATION").append(',')
            .append(csvCell(result.name())).append(',')
            .append(csvCell(result.status().name())).append(',')
            .append(csvCell(result.objectName())).append(',')
            .append(',')
            .append(',')
            .append(',')
            .append(csvCell(result.message())).append('\n'));
        report.errors().forEach(error -> builder
            .append("ERROR").append(',')
            .append(csvCell(error.phase())).append(',')
            .append(csvCell(error.actionCategory())).append(',')
            .append(csvCell(error.objectName())).append(',')
            .append(csvCell(error.chunkId())).append(',')
            .append(csvCell(error.databaseCode())).append(',')
            .append(csvCell(error.sqlText())).append(',')
            .append(csvCell(error.message())).append('\n'));
        report.readinessFindings().forEach(finding -> builder
            .append("READINESS").append(',')
            .append(csvCell(finding.code())).append(',')
            .append(csvCell(finding.severity().name())).append(',')
            .append(csvCell(finding.objectName())).append(',')
            .append(',')
            .append(',')
            .append(',')
            .append(csvCell(finding.description() + " | Column: " + finding.columnName() + " | Mitigation: " + finding.mitigation())).append('\n'));
        report.excludedObjectDecisions().forEach(decision -> builder
            .append("EXCLUSION").append(',')
            .append(csvCell(decision.objectName())).append(',')
            .append(csvCell(decision.status())).append(',')
            .append(csvCell(decision.objectType())).append(',')
            .append(',')
            .append(',')
            .append(',')
            .append(csvCell(decision.reason())).append('\n'));
        return builder.toString();
    }

    private String readinessCsv(MigrationReport report) {
        StringBuilder builder = new StringBuilder("code,severity,object_type,object_name,column_name,description,detection_method,mitigation,should_stop\n");
        report.readinessFindings().forEach(finding -> builder
            .append(csvCell(finding.code())).append(',')
            .append(csvCell(finding.severity().name())).append(',')
            .append(csvCell(finding.objectType())).append(',')
            .append(csvCell(finding.objectName())).append(',')
            .append(csvCell(finding.columnName())).append(',')
            .append(csvCell(finding.description())).append(',')
            .append(csvCell(finding.detectionMethod())).append(',')
            .append(csvCell(finding.mitigation())).append(',')
            .append(finding.shouldStop()).append('\n'));
        return builder.toString();
    }

    private String exclusionsCsv(MigrationReport report) {
        StringBuilder builder = new StringBuilder("object_type,object_name,status,reason\n");
        report.excludedObjectDecisions().forEach(decision -> builder
            .append(csvCell(decision.objectType())).append(',')
            .append(csvCell(decision.objectName())).append(',')
            .append(csvCell(decision.status())).append(',')
            .append(csvCell(decision.reason())).append('\n'));
        return builder.toString();
    }

    private String html(MigrationReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><body>");
        builder.append("<h1>Migration Report ").append(escapeHtml(report.runId())).append("</h1>");
        builder.append("<p>Status: ").append(escapeHtml(report.status())).append("</p>");
        builder.append("<p>Mode: ").append(escapeHtml(report.mode())).append("</p>");
        if (!report.skippedPhases().isEmpty()) {
            builder.append("<h2>Skipped Phases</h2><ul>");
            report.skippedPhases().forEach(phase -> builder.append("<li>").append(escapeHtml(phase)).append("</li>"));
            builder.append("</ul>");
        }
        
        if (!report.chunkStrategies().isEmpty()) {
            builder.append("<h2>Migration Strategies</h2>");
            builder.append("<table><thead><tr><th>Table Name</th><th>Chunk ID</th><th>Strategy</th><th>Column Name</th><th>Where Clause</th></tr></thead><tbody>");
            report.chunkStrategies().forEach(strategy -> builder.append("<tr><td>")
                .append(escapeHtml(strategy.tableName())).append("</td><td>")
                .append(escapeHtml(strategy.chunkId())).append("</td><td>")
                .append(escapeHtml(strategy.strategy())).append("</td><td>")
                .append(escapeHtml(strategy.columnName())).append("</td><td>")
                .append(escapeHtml(strategy.whereClause())).append("</td></tr>"));
            builder.append("</tbody></table>");
        }
        
        if (!report.readinessFindings().isEmpty()) {
            builder.append("<h2>Readiness Findings</h2>");
            builder.append("<table><thead><tr><th>Code</th><th>Severity</th><th>Object Type</th><th>Object Name</th><th>Column Name</th><th>Description</th><th>Detection Method</th><th>Mitigation</th><th>Should Stop</th></tr></thead><tbody>");
            report.readinessFindings().forEach(finding -> builder.append("<tr><td>")
                .append(escapeHtml(finding.code())).append("</td><td>")
                .append(escapeHtml(finding.severity().name())).append("</td><td>")
                .append(escapeHtml(finding.objectType())).append("</td><td>")
                .append(escapeHtml(finding.objectName())).append("</td><td>")
                .append(escapeHtml(finding.columnName())).append("</td><td>")
                .append(escapeHtml(finding.description())).append("</td><td>")
                .append(escapeHtml(finding.detectionMethod())).append("</td><td>")
                .append(escapeHtml(finding.mitigation())).append("</td><td>")
                .append(finding.shouldStop()).append("</td></tr>"));
            builder.append("</tbody></table>");
        }

        if (!report.excludedObjectDecisions().isEmpty()) {
            builder.append("<h2>Excluded Objects & Decisions</h2>");
            builder.append("<table><thead><tr><th>Object Type</th><th>Object Name</th><th>Status</th><th>Reason / Decision Note</th></tr></thead><tbody>");
            report.excludedObjectDecisions().forEach(decision -> builder.append("<tr><td>")
                .append(escapeHtml(decision.objectType())).append("</td><td>")
                .append(escapeHtml(decision.objectName())).append("</td><td>")
                .append(escapeHtml(decision.status())).append("</td><td>")
                .append(escapeHtml(decision.reason())).append("</td></tr>"));
            builder.append("</tbody></table>");
        }

        builder.append("<h2>Validations</h2>");
        builder.append("<table><thead><tr><th>Name</th><th>Status</th><th>Object</th><th>Message</th></tr></thead><tbody>");
        report.validations().forEach(result -> builder.append("<tr><td>")
            .append(escapeHtml(result.name())).append("</td><td>")
            .append(escapeHtml(result.status().name())).append("</td><td>")
            .append(escapeHtml(result.objectName())).append("</td><td>")
            .append(escapeHtml(result.message())).append("</td></tr>"));
        builder.append("</tbody></table>");
        if (!report.errors().isEmpty()) {
            builder.append("<h2>Errors</h2>");
            builder.append("<table><thead><tr><th>Phase</th><th>Category</th><th>Object</th><th>Chunk</th><th>Database Code</th><th>SQL</th><th>Message</th></tr></thead><tbody>");
            report.errors().forEach(error -> builder.append("<tr><td>")
                .append(escapeHtml(error.phase())).append("</td><td>")
                .append(escapeHtml(error.actionCategory())).append("</td><td>")
                .append(escapeHtml(error.objectName())).append("</td><td>")
                .append(escapeHtml(error.chunkId())).append("</td><td>")
                .append(escapeHtml(error.databaseCode())).append("</td><td>")
                .append(escapeHtml(error.sqlText())).append("</td><td>")
                .append(escapeHtml(error.message())).append("</td></tr>"));
            builder.append("</tbody></table>");
        }
        builder.append("</body></html>");
        return builder.toString();
    }

    private static String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.contains(",") && !safe.contains("\"") && !safe.contains("\n") && !safe.contains("\r")) {
            return safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
    private String strategiesCsv(MigrationReport report) {
        StringBuilder builder = new StringBuilder("table_name,chunk_id,strategy,column_name,where_clause\n");
        report.chunkStrategies().forEach(strategy -> builder
            .append(csvCell(strategy.tableName())).append(',')
            .append(csvCell(strategy.chunkId())).append(',')
            .append(csvCell(strategy.strategy())).append(',')
            .append(csvCell(strategy.columnName())).append(',')
            .append(csvCell(strategy.whereClause())).append('\n'));
        return builder.toString();
    }
}

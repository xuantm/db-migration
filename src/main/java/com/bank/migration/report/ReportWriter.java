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
        return builder.toString();
    }

    private String html(MigrationReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><body>");
        builder.append("<h1>Migration Report ").append(escapeHtml(report.runId())).append("</h1>");
        builder.append("<p>Status: ").append(escapeHtml(report.status())).append("</p>");
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
}

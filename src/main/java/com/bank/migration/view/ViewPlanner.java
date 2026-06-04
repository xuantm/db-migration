package com.bank.migration.view;

import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.ViewMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ViewPlanner {
    private final IdentifierRenderer targetRenderer;

    public ViewPlanner(@Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer) {
        this.targetRenderer = targetRenderer;
    }

    public ViewPlan plan(String targetSchema, ViewMetadata view) {
        if (view.status() == ObjectStatus.EXCLUDED) {
            return new ViewPlan(view.name(), ObjectStatus.EXCLUDED, "", List.of("View is excluded"));
        }

        List<String> notes = new ArrayList<>();
        if (view.status() == ObjectStatus.NEEDS_REVIEW) {
            notes.add("View status is NEEDS_REVIEW (e.g. references excluded object)");
        }

        String sqlBody = view.sql().trim();
        String lowerSql = sqlBody.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        if (lowerSql.contains("sys_context")) {
            notes.add("Oracle-specific SQL detected: sys_context");
        }
        if (lowerSql.contains("connect by")) {
            notes.add("Oracle-specific SQL detected: connect by");
        }
        if (lowerSql.contains("from dual") || lowerSql.contains("join dual")) {
            notes.add("Oracle-specific SQL detected: dual");
        }
        if (lowerSql.contains("(+)")) {
            notes.add("Oracle-specific outer join syntax detected: (+)");
        }
        if (lowerSql.contains("using nchar_cs") || lowerSql.contains("nchar_cs")) {
            notes.add("Oracle-specific character set translation detected: nchar_cs");
        }

        ObjectStatus status = (notes.isEmpty() && view.status() != ObjectStatus.NEEDS_REVIEW) 
            ? ObjectStatus.READY 
            : ObjectStatus.NEEDS_REVIEW;
        String sql = "create or replace view " + targetRenderer.renderQualifiedName(targetSchema, view.name())
            + " as " + sqlBody;
        return new ViewPlan(view.name(), status, sql, notes);
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}

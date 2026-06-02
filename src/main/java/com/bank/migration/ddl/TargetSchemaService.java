package com.bank.migration.ddl;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TargetSchemaService {
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate targetJdbc;

    public TargetSchemaService(@Qualifier("targetJdbc") JdbcTemplate targetJdbc) {
        this.targetJdbc = targetJdbc;
    }

    public void prepareCleanSchema(String targetSchema) {
        String schema = normalizeSchema(targetSchema);
        targetJdbc.execute("drop schema if exists " + schema + " cascade");
        targetJdbc.execute("create schema " + schema);
    }

    private static String normalizeSchema(String targetSchema) {
        if (targetSchema == null || targetSchema.isBlank()) {
            throw new IllegalArgumentException("Target schema must not be blank");
        }
        if (!SIMPLE_IDENTIFIER.matcher(targetSchema).matches()) {
            throw new IllegalArgumentException("Target schema must be a simple unquoted identifier");
        }
        return targetSchema.toLowerCase(Locale.ROOT);
    }
}

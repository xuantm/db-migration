package com.bank.migration.ddl;

import com.bank.migration.identifier.IdentifierRenderer;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TargetSchemaService {
    private static final Pattern SIMPLE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;

    public TargetSchemaService(
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
    }

    public void prepareCleanSchema(String targetSchema) {
        validateSchema(targetSchema);
        String schema = targetRenderer.render(targetSchema);
        targetJdbc.execute("drop schema if exists " + schema + " cascade");
        targetJdbc.execute("create schema " + schema);
    }

    private static void validateSchema(String targetSchema) {
        if (targetSchema == null || targetSchema.isBlank()) {
            throw new IllegalArgumentException("Target schema must not be blank");
        }
        if (!SIMPLE_IDENTIFIER.matcher(targetSchema).matches()) {
            throw new IllegalArgumentException("Target schema must be a simple unquoted identifier");
        }
    }
}

package com.bank.migration.identifier;

import com.bank.migration.dialect.DatabaseDialect;

public class IdentifierRenderer {
    private final DatabaseDialect dialect;

    public IdentifierRenderer(DatabaseDialect dialect) {
        this.dialect = dialect;
    }

    public String render(String rawIdentifier) {
        return dialect.renderIdentifier(rawIdentifier);
    }

    public String render(Identifier identifier) {
        return identifier == null ? "" : render(identifier.value());
    }

    public String renderQualifiedName(String schema, String objectName) {
        return dialect.renderQualifiedName(schema, objectName);
    }

    public String physicalName(String rawIdentifier) {
        if (rawIdentifier == null) {
            return "";
        }
        String rendered = render(rawIdentifier);
        if (rendered.startsWith("\"") && rendered.endsWith("\"")) {
            return rendered.substring(1, rendered.length() - 1);
        }
        return rendered;
    }
}

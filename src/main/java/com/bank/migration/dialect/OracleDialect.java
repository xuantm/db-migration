package com.bank.migration.dialect;

import com.bank.migration.identifier.ReservedWordRegistry;
import java.util.Locale;

public class OracleDialect implements DatabaseDialect {
    @Override
    public String renderIdentifier(String rawIdentifier) {
        if (rawIdentifier == null) {
            return "";
        }
        return rawIdentifier;
    }

    @Override
    public String renderQualifiedName(String schema, String objectName) {
        String renderedSchema = renderIdentifier(schema);
        String renderedObject = renderIdentifier(objectName);
        if (renderedSchema.isEmpty()) {
            return renderedObject;
        }
        return renderedSchema + "." + renderedObject;
    }

    @Override
    public boolean isReservedWord(String identifier) {
        return ReservedWordRegistry.isReserved(identifier);
    }
}

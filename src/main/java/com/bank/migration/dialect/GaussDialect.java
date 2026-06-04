package com.bank.migration.dialect;

import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.ReservedWordRegistry;
import java.util.Locale;

public class GaussDialect implements DatabaseDialect {
    private final IdentifierMappingPolicy policy;

    public GaussDialect(IdentifierMappingPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String renderIdentifier(String rawIdentifier) {
        if (rawIdentifier == null) {
            return "";
        }
        if (isReservedWord(rawIdentifier)) {
            if (policy == IdentifierMappingPolicy.QUOTE) {
                return "\"" + rawIdentifier.toUpperCase(Locale.ROOT) + "\"";
            } else {
                return rawIdentifier.toLowerCase(Locale.ROOT) + "_";
            }
        }
        return rawIdentifier.toLowerCase(Locale.ROOT);
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

package com.bank.migration.validate;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DuplicateKeyValidator {
    private final IdentifierRenderer targetRenderer;

    public DuplicateKeyValidator(
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.targetRenderer = targetRenderer;
    }

    public String duplicateSql(String targetSchema, TableMetadata table, KeyMetadata key) {
        if (key.columns().isEmpty()) {
            throw new IllegalArgumentException("Key columns must not be empty");
        }
        String columns = String.join(", ", key.columns().stream().map(targetRenderer::render).toList());
        String nonNullChecks = String.join(
            " and ",
            key.columns().stream().map(targetRenderer::render).map(column -> column + " is not null").toList()
        );
        return "select " + columns + ", count(*) from " + targetRenderer.renderQualifiedName(targetSchema, table.name())
            + " where " + nonNullChecks
            + " group by " + columns
            + " having count(*) > 1";
    }
}

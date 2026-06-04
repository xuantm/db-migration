package com.bank.migration.validate;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Stale source-manifest style foreign key validator.
 * Assumes the parent table exists in the targetSchema and enforces MATCH SIMPLE semantics.
 * Bypassed in DATA_ONLY mode in favor of TargetForeignKeyValidator to support external/retained parents.
 */
@Service
public class ForeignKeyValidator {
    private final IdentifierRenderer targetRenderer;

    public ForeignKeyValidator(
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.targetRenderer = targetRenderer;
    }

    public String orphanSql(String targetSchema, TableMetadata childTable, KeyMetadata foreignKey) {
        if (foreignKey.columns().isEmpty() || foreignKey.columns().size() != foreignKey.referencedColumns().size()) {
            throw new IllegalArgumentException("Foreign key columns and referenced columns must be present and aligned");
        }
        String child = targetRenderer.renderQualifiedName(targetSchema, childTable.name());
        String parent = targetRenderer.renderQualifiedName(targetSchema, foreignKey.referencedTable());
        List<String> conditions = new ArrayList<>();
        List<String> childNotNullChecks = new ArrayList<>();
        for (int i = 0; i < foreignKey.columns().size(); i++) {
            String childColumn = targetRenderer.render(foreignKey.columns().get(i));
            conditions.add("c." + childColumn + " = p." + targetRenderer.render(foreignKey.referencedColumns().get(i)));
            childNotNullChecks.add("c." + childColumn + " is not null");
        }
        String parentNullCheck = "p." + targetRenderer.render(foreignKey.referencedColumns().getFirst()) + " is null";
        return "select count(*) from " + child + " c left join " + parent + " p on "
            + String.join(" and ", conditions) + " where "
            + String.join(" and ", childNotNullChecks)
            + " and " + parentNullCheck;
    }
}

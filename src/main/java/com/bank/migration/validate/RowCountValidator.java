package com.bank.migration.validate;

import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RowCountValidator {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer sourceRenderer;
    private final IdentifierRenderer targetRenderer;

    public RowCountValidator(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("sourceIdentifierRenderer") IdentifierRenderer sourceRenderer,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
        this.sourceRenderer = sourceRenderer;
        this.targetRenderer = targetRenderer;
    }

    public ValidationResult validate(TableMetadata table, String targetSchema) {
        Long sourceCount = sourceJdbc.queryForObject(
            "select count(*) from " + sourceRenderer.renderQualifiedName(table.schema(), table.name()),
            Long.class
        );
        Long targetCount = targetJdbc.queryForObject(
            "select count(*) from " + targetRenderer.renderQualifiedName(targetSchema, table.name()),
            Long.class
        );
        boolean pass = sourceCount != null && sourceCount.equals(targetCount);
        return new ValidationResult(
            "row-count",
            pass ? ValidationStatus.PASS : ValidationStatus.FAIL,
            table.name(),
            "source=" + sourceCount + " target=" + targetCount
        );
    }
}

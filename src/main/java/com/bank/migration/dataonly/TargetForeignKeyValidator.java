package com.bank.migration.dataonly;

import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.validate.ValidationResult;
import com.bank.migration.validate.ValidationStatus;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TargetForeignKeyValidator {
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;
    private final TargetForeignKeyScanner scanner;

    public TargetForeignKeyValidator(
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer,
        TargetForeignKeyScanner scanner
    ) {
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
        this.scanner = scanner;
    }

    public List<ValidationResult> validate(String targetSchema, Set<String> includedTables) {
        return scanner.scan(targetSchema, includedTables).stream()
            .map(fk -> validate(fk))
            .toList();
    }

    private ValidationResult validate(TargetForeignKeyMetadata fk) {
        String join = IntStream.range(0, fk.childColumns().size())
            .mapToObj(i -> "c." + targetRenderer.render(fk.childColumns().get(i)) + " = p." + targetRenderer.render(fk.parentColumns().get(i)))
            .reduce((left, right) -> left + " and " + right)
            .orElseThrow();
        String notNull = fk.childColumns().stream()
            .map(column -> "c." + targetRenderer.render(column) + " is not null")
            .reduce((left, right) -> left + " and " + right)
            .orElse("true");
        String parentMissing = fk.parentColumns().stream()
            .map(column -> "p." + targetRenderer.render(column) + " is null")
            .findFirst()
            .orElse("false");

        boolean isMatchFull = "f".equalsIgnoreCase(fk.confmatchtype());
        boolean isComposite = fk.childColumns().size() > 1;

        String whereClause;
        if (isMatchFull && isComposite) {
            String anyNull = fk.childColumns().stream()
                .map(column -> "c." + targetRenderer.render(column) + " is null")
                .reduce((left, right) -> left + " or " + right)
                .orElse("false");
            String anyNotNull = fk.childColumns().stream()
                .map(column -> "c." + targetRenderer.render(column) + " is not null")
                .reduce((left, right) -> left + " or " + right)
                .orElse("false");
            whereClause = "((" + anyNull + ") and (" + anyNotNull + ")) or (" + notNull + " and " + parentMissing + ")";
        } else {
            whereClause = notNull + " and " + parentMissing;
        }

        String sql = "select count(*) from "
            + targetRenderer.renderQualifiedName(fk.childSchema(), fk.childTable()) + " c left join "
            + targetRenderer.renderQualifiedName(fk.parentSchema(), fk.parentTable()) + " p on " + join
            + " where " + whereClause;

        Long count = targetJdbc.queryForObject(sql, Long.class);
        long orphans = count == null ? 0L : count;
        return new ValidationResult(
            "target-foreign-key-orphans",
            orphans == 0L ? ValidationStatus.PASS : ValidationStatus.FAIL,
            fk.childTable().toUpperCase() + "." + fk.constraintName(),
            "orphans=" + orphans
        );
    }
}

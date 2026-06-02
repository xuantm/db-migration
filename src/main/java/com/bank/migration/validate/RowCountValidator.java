package com.bank.migration.validate;

import com.bank.migration.domain.TableMetadata;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RowCountValidator {
    private final JdbcTemplate sourceJdbc;
    private final JdbcTemplate targetJdbc;

    public RowCountValidator(
        @Qualifier("sourceJdbc") JdbcTemplate sourceJdbc,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc
    ) {
        this.sourceJdbc = sourceJdbc;
        this.targetJdbc = targetJdbc;
    }

    public ValidationResult validate(TableMetadata table, String targetSchema) {
        Long sourceCount = sourceJdbc.queryForObject("select count(*) from " + table.schema() + "." + table.name(), Long.class);
        Long targetCount = targetJdbc.queryForObject(
            "select count(*) from " + lower(targetSchema) + "." + lower(table.name()),
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

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}

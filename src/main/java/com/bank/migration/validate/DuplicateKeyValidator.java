package com.bank.migration.validate;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class DuplicateKeyValidator {
    public String duplicateSql(String targetSchema, TableMetadata table, KeyMetadata key) {
        if (key.columns().isEmpty()) {
            throw new IllegalArgumentException("Key columns must not be empty");
        }
        String columns = String.join(", ", key.columns().stream().map(DuplicateKeyValidator::lower).toList());
        String nonNullChecks = String.join(
            " and ",
            key.columns().stream().map(DuplicateKeyValidator::lower).map(column -> column + " is not null").toList()
        );
        return "select " + columns + ", count(*) from " + lower(targetSchema) + "." + lower(table.name())
            + " where " + nonNullChecks
            + " group by " + columns
            + " having count(*) > 1";
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}

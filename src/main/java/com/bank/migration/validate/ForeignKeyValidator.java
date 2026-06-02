package com.bank.migration.validate;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.TableMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ForeignKeyValidator {
    public String orphanSql(String targetSchema, TableMetadata childTable, KeyMetadata foreignKey) {
        if (foreignKey.columns().isEmpty() || foreignKey.columns().size() != foreignKey.referencedColumns().size()) {
            throw new IllegalArgumentException("Foreign key columns and referenced columns must be present and aligned");
        }
        String child = lower(targetSchema) + "." + lower(childTable.name());
        String parent = lower(targetSchema) + "." + lower(foreignKey.referencedTable());
        List<String> conditions = new ArrayList<>();
        List<String> childNotNullChecks = new ArrayList<>();
        for (int i = 0; i < foreignKey.columns().size(); i++) {
            String childColumn = lower(foreignKey.columns().get(i));
            conditions.add("c." + childColumn + " = p." + lower(foreignKey.referencedColumns().get(i)));
            childNotNullChecks.add("c." + childColumn + " is not null");
        }
        String parentNullCheck = "p." + lower(foreignKey.referencedColumns().getFirst()) + " is null";
        return "select count(*) from " + child + " c left join " + parent + " p on "
            + String.join(" and ", conditions) + " where "
            + String.join(" and ", childNotNullChecks)
            + " and " + parentNullCheck;
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}

package com.bank.migration.dataonly;

import java.util.List;

public record TargetForeignKeyMetadata(
    String constraintName,
    String childSchema,
    String childTable,
    List<String> childColumns,
    String parentSchema,
    String parentTable,
    List<String> parentColumns,
    String confmatchtype
) {
    public TargetForeignKeyMetadata {
        childColumns = List.copyOf(childColumns);
        parentColumns = List.copyOf(parentColumns);
    }

    public TargetForeignKeyMetadata(
        String constraintName,
        String childSchema,
        String childTable,
        List<String> childColumns,
        String parentSchema,
        String parentTable,
        List<String> parentColumns
    ) {
        this(constraintName, childSchema, childTable, childColumns, parentSchema, parentTable, parentColumns, "s");
    }
}

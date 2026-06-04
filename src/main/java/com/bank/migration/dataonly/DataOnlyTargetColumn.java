package com.bank.migration.dataonly;

public record DataOnlyTargetColumn(
    String name,
    boolean nullable,
    String defaultExpression,
    String identityGeneration,
    String generationExpression
) {
    public boolean canBeOmittedFromInsert() {
        return nullable
            || hasText(defaultExpression)
            || hasText(identityGeneration)
            || hasText(generationExpression);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

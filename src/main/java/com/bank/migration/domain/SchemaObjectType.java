package com.bank.migration.domain;

public enum SchemaObjectType {
    SEQUENCE,
    SYNONYM,
    MATERIALIZED_VIEW,
    TRIGGER,
    PROCEDURE,
    FUNCTION,
    PACKAGE,
    TYPE,
    CHECK_CONSTRAINT,
    FUNCTION_BASED_INDEX,
    BITMAP_INDEX
}

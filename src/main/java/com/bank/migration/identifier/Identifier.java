package com.bank.migration.identifier;

public record Identifier(String value) {
    public static Identifier of(String value) {
        return new Identifier(value);
    }
}

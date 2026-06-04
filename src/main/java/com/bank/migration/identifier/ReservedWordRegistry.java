package com.bank.migration.identifier;

import java.util.Set;
import java.util.Locale;

public class ReservedWordRegistry {
    private static final Set<String> RESERVED_WORDS = Set.of(
        "LIMIT", "USER", "ORDER", "GROUP", "SELECT", "WHERE",
        "TABLE", "INDEX", "CONSTRAINT", "PRIMARY", "FOREIGN", "UNIQUE"
    );

    public static boolean isReserved(String word) {
        if (word == null) {
            return false;
        }
        return RESERVED_WORDS.contains(word.toUpperCase(Locale.ROOT));
    }
}

package com.bank.migration.ddl;

import com.bank.migration.domain.ColumnMetadata;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class OracleToGaussTypeMapper {
    private static final Pattern TIMESTAMP_WITH_ZONE_PATTERN = Pattern.compile(
        "^TIMESTAMP(?:\\(\\d+\\))?(?: WITH TIME ZONE| WITH LOCAL TIME ZONE)$"
    );
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^TIMESTAMP(?:\\(\\d+\\))?$");

    public GaussType map(ColumnMetadata column) {
        String oracleType = normalize(column.oracleType());
        return switch (oracleType) {
            case "NUMBER" -> mapNumber(column);
            case "VARCHAR2", "NVARCHAR2" -> new GaussType(
                "varchar(" + lengthOrDefault(column.precision(), 4000) + ")",
                false,
                List.of()
            );
            case "CHAR", "NCHAR" -> new GaussType(
                "char(" + lengthOrDefault(column.precision(), 1) + ")",
                false,
                List.of()
            );
            case "DATE" -> new GaussType("timestamp", false, List.of());
            case "CLOB", "NCLOB" -> new GaussType("text", false, List.of());
            case "BLOB", "RAW" -> new GaussType("bytea", false, List.of());
            default -> isTimestampWithZoneType(oracleType)
                ? new GaussType("timestamp with time zone", false, List.of())
                : isTimestampType(oracleType)
                ? new GaussType("timestamp", false, List.of())
                : new GaussType(
                    "text",
                    true,
                    List.of("Unsupported Oracle type " + oracleType)
                );
        };
    }

    private GaussType mapNumber(ColumnMetadata column) {
        Integer precision = column.precision();
        Integer scale = column.scale() == null ? 0 : column.scale();

        if (precision == null) {
            return new GaussType(
                "numeric",
                false,
                List.of("NUMBER without precision mapped to numeric")
            );
        }

        if (scale == 0) {
            if (precision <= 4) {
                return new GaussType("smallint", false, List.of());
            }
            if (precision <= 9) {
                return new GaussType("integer", false, List.of());
            }
            if (precision <= 18) {
                return new GaussType("bigint", false, List.of());
            }
            return new GaussType("numeric(" + precision + ",0)", false, List.of());
        }

        return new GaussType("numeric(" + precision + "," + scale + ")", false, List.of());
    }

    private static int lengthOrDefault(Integer precision, int defaultValue) {
        return precision == null ? defaultValue : precision;
    }

    private static String normalize(String oracleType) {
        return oracleType == null ? "" : oracleType.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean isTimestampType(String oracleType) {
        return TIMESTAMP_PATTERN.matcher(oracleType).matches();
    }

    private static boolean isTimestampWithZoneType(String oracleType) {
        return TIMESTAMP_WITH_ZONE_PATTERN.matcher(oracleType).matches();
    }
}

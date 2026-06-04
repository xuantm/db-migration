package com.bank.migration.types;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.domain.ColumnMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OracleToGaussTypeMapper {
    private static final Pattern TIMESTAMP_WITH_ZONE_PATTERN = Pattern.compile(
        "^TIMESTAMP(?:\\(\\d+\\))?(?: WITH TIME ZONE| WITH LOCAL TIME ZONE)$"
    );
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^TIMESTAMP(?:\\(\\d+\\))?$");

    private final UnsupportedTypePolicy policy;

    @Autowired
    public OracleToGaussTypeMapper(MigrationProperties properties) {
        this(properties == null ? UnsupportedTypePolicy.FAIL : properties.unsupportedTypePolicy());
    }

    public OracleToGaussTypeMapper() {
        this(UnsupportedTypePolicy.FAIL);
    }

    public OracleToGaussTypeMapper(UnsupportedTypePolicy policy) {
        this.policy = policy == null ? UnsupportedTypePolicy.FAIL : policy;
    }

    public TypeMappingResult map(ColumnMetadata column) {
        String oracleType = normalize(column.oracleType());
        if (oracleType.startsWith("INTERVAL")) {
            return new TypeMappingResult("interval", TypeRisk.READY, List.of(), true);
        }

        switch (oracleType) {
            case "NUMBER":
                return mapNumber(column);
            case "VARCHAR2":
            case "NVARCHAR2":
                return new TypeMappingResult(
                    "varchar(" + lengthOrDefault(column.precision(), 4000) + ")",
                    TypeRisk.READY,
                    List.of(),
                    true
                );
            case "CHAR":
            case "NCHAR":
                return new TypeMappingResult(
                    "char(" + lengthOrDefault(column.precision(), 1) + ")",
                    TypeRisk.READY,
                    List.of(),
                    true
                );
            case "DATE":
                return new TypeMappingResult("timestamp", TypeRisk.READY, List.of(), true);
            case "CLOB":
            case "NCLOB":
                return new TypeMappingResult("text", TypeRisk.REVIEW, List.of("CLOB type requires LOB conversion review"), true);
            case "BLOB":
                return new TypeMappingResult("bytea", TypeRisk.REVIEW, List.of("BLOB type requires LOB conversion review"), true);
            case "RAW":
                return new TypeMappingResult("bytea", TypeRisk.REVIEW, List.of("RAW type requires conversion review"), true);
            case "BFILE":
            case "LONG":
            case "XMLTYPE":
            case "SDO_GEOMETRY":
                return handleBlockedUnsupported(oracleType);
            default:
                if (isTimestampWithZoneType(oracleType)) {
                    return new TypeMappingResult("timestamp with time zone", TypeRisk.READY, List.of(), true);
                } else if (isTimestampType(oracleType)) {
                    return new TypeMappingResult("timestamp", TypeRisk.READY, List.of(), true);
                } else {
                    return handleUnsupported(oracleType);
                }
        }
    }

    private TypeMappingResult mapNumber(ColumnMetadata column) {
        Integer precision = column.precision();
        Integer scale = column.scale() == null ? 0 : column.scale();

        if (precision == null) {
            return new TypeMappingResult(
                "numeric",
                TypeRisk.REVIEW,
                List.of("NUMBER without precision mapped to numeric"),
                true
            );
        }

        if (scale == 0) {
            if (precision <= 4) {
                return new TypeMappingResult("smallint", TypeRisk.READY, List.of(), true);
            }
            if (precision <= 9) {
                return new TypeMappingResult("integer", TypeRisk.READY, List.of(), true);
            }
            if (precision <= 18) {
                return new TypeMappingResult("bigint", TypeRisk.READY, List.of(), true);
            }
            return new TypeMappingResult("numeric(" + precision + ",0)", TypeRisk.READY, List.of(), true);
        }

        return new TypeMappingResult("numeric(" + precision + "," + scale + ")", TypeRisk.READY, List.of(), true);
    }

    private TypeMappingResult handleBlockedUnsupported(String oracleType) {
        return new TypeMappingResult(
            null,
            TypeRisk.BLOCKED,
            List.of("Unsupported Oracle type " + oracleType),
            false
        );
    }

    private TypeMappingResult handleUnsupported(String oracleType) {
        if (policy == UnsupportedTypePolicy.FALLBACK_TO_TEXT) {
            return new TypeMappingResult(
                "text",
                TypeRisk.REVIEW,
                List.of("Unsupported Oracle type " + oracleType + " fell back to text"),
                true
            );
        } else {
            return new TypeMappingResult(
                null,
                TypeRisk.BLOCKED,
                List.of("Unsupported Oracle type " + oracleType),
                false
            );
        }
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

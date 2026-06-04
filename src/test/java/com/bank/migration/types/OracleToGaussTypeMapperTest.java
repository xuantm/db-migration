package com.bank.migration.types;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ColumnMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OracleToGaussTypeMapperTest {
    private final OracleToGaussTypeMapper mapper = new OracleToGaussTypeMapper();

    @ParameterizedTest
    @CsvSource({
        "NUMBER, 4, 0, smallint, READY, true",
        "NUMBER, 9, 0, integer, READY, true",
        "NUMBER, 18, 0, bigint, READY, true",
        "VARCHAR2, 20, , varchar(20), READY, true",
        "DATE, , , timestamp, READY, true"
    })
    void mapsStandardTypesSuccessfully(String oracleType, Integer precision, Integer scale, String expectedSqlType, String expectedRisk, boolean canGenerate) {
        ColumnMetadata col = new ColumnMetadata("COL", oracleType, precision, scale, true, null);
        TypeMappingResult result = mapper.map(col);
        assertThat(result.targetSqlType()).isEqualTo(expectedSqlType);
        assertThat(result.risk().name()).isEqualTo(expectedRisk);
        assertThat(result.canGenerateDdl()).isEqualTo(canGenerate);
    }

    @Test
    void mapsNumberNullNullToReview() {
        ColumnMetadata col = new ColumnMetadata("COL", "NUMBER", null, null, true, null);
        TypeMappingResult result = mapper.map(col);
        assertThat(result.targetSqlType()).isEqualTo("numeric");
        assertThat(result.risk()).isEqualTo(TypeRisk.REVIEW);
        assertThat(result.canGenerateDdl()).isTrue();
        assertThat(result.notes()).containsExactly("NUMBER without precision mapped to numeric");
    }

    @Test
    void mapsClobAndBlobToReview() {
        ColumnMetadata clobCol = new ColumnMetadata("COL_CLOB", "CLOB", null, null, true, null);
        TypeMappingResult clobResult = mapper.map(clobCol);
        assertThat(clobResult.targetSqlType()).isEqualTo("text");
        assertThat(clobResult.risk()).isEqualTo(TypeRisk.REVIEW);
        assertThat(clobResult.canGenerateDdl()).isTrue();

        ColumnMetadata blobCol = new ColumnMetadata("COL_BLOB", "BLOB", null, null, true, null);
        TypeMappingResult blobResult = mapper.map(blobCol);
        assertThat(blobResult.targetSqlType()).isEqualTo("bytea");
        assertThat(blobResult.risk()).isEqualTo(TypeRisk.REVIEW);
        assertThat(blobResult.canGenerateDdl()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "BFILE",
        "LONG",
        "XMLTYPE",
        "SDO_GEOMETRY"
    })
    void blocksUnsupportedTypesByDefault(String unsupportedType) {
        ColumnMetadata col = new ColumnMetadata("COL", unsupportedType, null, null, true, null);
        TypeMappingResult result = mapper.map(col);
        assertThat(result.targetSqlType()).isNull();
        assertThat(result.risk()).isEqualTo(TypeRisk.BLOCKED);
        assertThat(result.canGenerateDdl()).isFalse();
        assertThat(result.notes()).containsExactly("Unsupported Oracle type " + unsupportedType);
    }

    @Test
    void fallsBackToTextWhenConfigured() {
        OracleToGaussTypeMapper fallbackMapper = new OracleToGaussTypeMapper(UnsupportedTypePolicy.FALLBACK_TO_TEXT);
        ColumnMetadata col = new ColumnMetadata("COL", "MY_CUSTOM_TYPE", null, null, true, null);
        TypeMappingResult result = fallbackMapper.map(col);
        assertThat(result.targetSqlType()).isEqualTo("text");
        assertThat(result.risk()).isEqualTo(TypeRisk.REVIEW);
        assertThat(result.canGenerateDdl()).isTrue();
        assertThat(result.notes()).containsExactly("Unsupported Oracle type MY_CUSTOM_TYPE fell back to text");
    }

    @ParameterizedTest
    @CsvSource({
        "BFILE",
        "LONG",
        "XMLTYPE",
        "SDO_GEOMETRY"
    })
    void remainsBlockedEvenWithFallbackPolicy(String unsupportedType) {
        OracleToGaussTypeMapper fallbackMapper = new OracleToGaussTypeMapper(UnsupportedTypePolicy.FALLBACK_TO_TEXT);
        ColumnMetadata col = new ColumnMetadata("COL", unsupportedType, null, null, true, null);
        TypeMappingResult result = fallbackMapper.map(col);
        assertThat(result.targetSqlType()).isNull();
        assertThat(result.risk()).isEqualTo(TypeRisk.BLOCKED);
        assertThat(result.canGenerateDdl()).isFalse();
        assertThat(result.notes()).containsExactly("Unsupported Oracle type " + unsupportedType);
    }
}

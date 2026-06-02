package com.bank.migration.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ColumnMetadata;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OracleToGaussTypeMapperTest {
    private final OracleToGaussTypeMapper mapper = new OracleToGaussTypeMapper();

    @Test
    void mapsIntegerNumbersSafely() {
        GaussType bigint = mapper.map(new ColumnMetadata("ID", "NUMBER", 18, 0, false, null));
        GaussType numeric = mapper.map(new ColumnMetadata("AMOUNT", "NUMBER", 18, 2, true, null));
        GaussType unspecified = mapper.map(new ColumnMetadata("RATIO", "NUMBER", null, null, true, null));
        GaussType conservativeNumeric = mapper.map(new ColumnMetadata("LEGACY_ID", "NUMBER", 19, 0, false, null));

        assertThat(bigint.sqlType()).isEqualTo("bigint");
        assertThat(bigint.needsReview()).isFalse();
        assertThat(bigint.notes()).isEmpty();

        assertThat(numeric.sqlType()).isEqualTo("numeric(18,2)");
        assertThat(numeric.needsReview()).isFalse();
        assertThat(numeric.notes()).isEmpty();

        assertThat(conservativeNumeric.sqlType()).isEqualTo("numeric(19,0)");
        assertThat(conservativeNumeric.needsReview()).isFalse();
        assertThat(conservativeNumeric.notes()).isEmpty();

        assertThat(unspecified.sqlType()).isEqualTo("numeric");
        assertThat(unspecified.needsReview()).isFalse();
        assertThat(unspecified.notes()).containsExactly("NUMBER without precision mapped to numeric");
    }

    @Test
    void mapsCommonTimestampVariantsToTimestamp() {
        GaussType plain = mapper.map(new ColumnMetadata("CREATED_AT", "TIMESTAMP(3)", null, null, true, null));
        GaussType withTimeZone = mapper.map(new ColumnMetadata("CREATED_AT", "TIMESTAMP WITH TIME ZONE", null, null, true, null));
        GaussType withLocalTimeZone = mapper.map(new ColumnMetadata("CREATED_AT", "TIMESTAMP(6) WITH LOCAL TIME ZONE", null, null, true, null));

        assertThat(plain.sqlType()).isEqualTo("timestamp");
        assertThat(plain.needsReview()).isFalse();
        assertThat(withTimeZone.sqlType()).isEqualTo("timestamp with time zone");
        assertThat(withTimeZone.needsReview()).isFalse();
        assertThat(withLocalTimeZone.sqlType()).isEqualTo("timestamp with time zone");
        assertThat(withLocalTimeZone.needsReview()).isFalse();
    }

    @Test
    void marksUnsupportedTypesAsReview() {
        GaussType mapped = mapper.map(new ColumnMetadata("PAYLOAD", "XMLTYPE", null, null, true, null));

        assertThat(mapped.sqlType()).isEqualTo("text");
        assertThat(mapped.needsReview()).isTrue();
        assertThat(mapped.notes()).containsExactly("Unsupported Oracle type XMLTYPE");
    }

    @Test
    void defensivelyCopiesNotesAndHandlesNull() {
        List<String> notes = new ArrayList<>(List.of("original"));
        GaussType type = new GaussType("text", true, notes);
        notes.add("mutated");

        assertThat(type.notes()).containsExactly("original");
        assertThat(new GaussType("text", false, null).notes()).isEmpty();
    }
}

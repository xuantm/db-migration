package com.bank.migration.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ColumnMetadata;
import org.junit.jupiter.api.Test;

class OracleToGaussTypeMapperTest {
    private final OracleToGaussTypeMapper mapper = new OracleToGaussTypeMapper();

    @Test
    void mapsIntegerNumbersSafely() {
        GaussType bigint = mapper.map(new ColumnMetadata("ID", "NUMBER", 19, 0, false, null));
        GaussType numeric = mapper.map(new ColumnMetadata("AMOUNT", "NUMBER", 18, 2, true, null));

        assertThat(bigint.sqlType()).isEqualTo("bigint");
        assertThat(bigint.needsReview()).isFalse();
        assertThat(bigint.notes()).isEmpty();

        assertThat(numeric.sqlType()).isEqualTo("numeric(18,2)");
        assertThat(numeric.needsReview()).isFalse();
        assertThat(numeric.notes()).isEmpty();
    }

    @Test
    void marksUnsupportedTypesAsReview() {
        GaussType mapped = mapper.map(new ColumnMetadata("PAYLOAD", "XMLTYPE", null, null, true, null));

        assertThat(mapped.sqlType()).isEqualTo("text");
        assertThat(mapped.needsReview()).isTrue();
        assertThat(mapped.notes()).containsExactly("Unsupported Oracle type XMLTYPE");
    }
}

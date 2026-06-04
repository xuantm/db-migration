package com.bank.migration.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;

class DuplicateKeyValidatorTest {
    private final DuplicateKeyValidator validator = new DuplicateKeyValidator(
        new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.QUOTE))
    );

    @Test
    void buildsDuplicateGroupQueryIgnoringNullKeyValues() {
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        KeyMetadata key = new KeyMetadata("UK_ACCOUNT_NO", "UNIQUE", List.of("BRANCH_ID", "ACCOUNT_NO"), null, null);

        String sql = validator.duplicateSql("bank_core", table, key);

        assertThat(sql).isEqualTo(
            "select branch_id, account_no, count(*) from bank_core.account"
                + " where branch_id is not null and account_no is not null"
                + " group by branch_id, account_no having count(*) > 1"
        );
    }

    @Test
    void rejectsKeysWithoutColumns() {
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        KeyMetadata key = new KeyMetadata("UK_EMPTY", "UNIQUE", List.of(), null, null);

        assertThatThrownBy(() -> validator.duplicateSql("bank_core", table, key))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Key columns must not be empty");
    }
}

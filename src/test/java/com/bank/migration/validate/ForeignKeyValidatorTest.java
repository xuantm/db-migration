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

class ForeignKeyValidatorTest {
    private final ForeignKeyValidator validator = new ForeignKeyValidator(
        new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.QUOTE))
    );

    @Test
    void buildsOrphanSqlThatIgnoresNullableChildKeys() {
        TableMetadata child = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        KeyMetadata fk = new KeyMetadata("FK_ACCOUNT_CUSTOMER", "FOREIGN_KEY", List.of("CUSTOMER_ID"), "CUSTOMER", List.of("ID"));

        String sql = validator.orphanSql("BANK_CORE", child, fk);

        assertThat(sql).isEqualTo(
            "select count(*) from bank_core.account c left join bank_core.customer p on "
                + "c.customer_id = p.id where c.customer_id is not null and p.id is null"
        );
    }

    @Test
    void rejectsMismatchedForeignKeyColumns() {
        TableMetadata child = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        KeyMetadata fk = new KeyMetadata("FK_BAD", "FOREIGN_KEY", List.of("CUSTOMER_ID"), "CUSTOMER", List.of());

        assertThatThrownBy(() -> validator.orphanSql("BANK_CORE", child, fk))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("aligned");
    }
}

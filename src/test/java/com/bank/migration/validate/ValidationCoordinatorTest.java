package com.bank.migration.validate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ValidationCoordinatorTest {
    @Mock RowCountValidator rowCountValidator;
    @Mock DuplicateKeyValidator duplicateKeyValidator;
    @Mock ForeignKeyValidator foreignKeyValidator;
    @Mock JdbcTemplate targetJdbc;

    @Test
    void validatesRowCountsUniqueKeysAndForeignKeys() {
        KeyMetadata primaryKey = new KeyMetadata("PK_ACCOUNT", "PRIMARY_KEY", List.of("ID"), null, null);
        KeyMetadata foreignKey = new KeyMetadata("FK_ACCOUNT_CUSTOMER", "FOREIGN_KEY", List.of("CUSTOMER_ID"), "CUSTOMER", List.of("ID"));
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(),
            List.of(primaryKey, foreignKey),
            List.of()
        );
        MigrationManifest manifest = new MigrationManifest("run-001", "BANK_CORE", List.of(table), List.of());
        ValidationResult rowCount = new ValidationResult("row-count", ValidationStatus.PASS, "ACCOUNT", "source=1 target=1");
        when(rowCountValidator.validate(table, "bank_core")).thenReturn(rowCount);
        when(duplicateKeyValidator.duplicateSql("bank_core", table, primaryKey))
            .thenReturn("select id, count(*) from bank_core.account where id is not null group by id having count(*) > 1");
        when(targetJdbc.queryForObject(
            "select count(*) from (select id, count(*) from bank_core.account where id is not null group by id having count(*) > 1) duplicate_groups",
            Long.class
        )).thenReturn(0L);
        when(foreignKeyValidator.orphanSql("bank_core", table, foreignKey)).thenReturn("select count(*) from bank_core.account where missing_parent");
        when(targetJdbc.queryForObject("select count(*) from bank_core.account where missing_parent", Long.class)).thenReturn(2L);

        List<ValidationResult> results = new ValidationCoordinator(
            rowCountValidator,
            duplicateKeyValidator,
            foreignKeyValidator,
            targetJdbc
        ).validate(manifest, "bank_core");

        assertThat(results).contains(rowCount);
        assertThat(results).anySatisfy(result -> {
            assertThat(result.name()).isEqualTo("duplicate-key");
            assertThat(result.status()).isEqualTo(ValidationStatus.PASS);
            assertThat(result.message()).isEqualTo("duplicateGroups=0");
        });
        assertThat(results).anySatisfy(result -> {
            assertThat(result.name()).isEqualTo("foreign-key-orphans");
            assertThat(result.status()).isEqualTo(ValidationStatus.FAIL);
            assertThat(result.message()).isEqualTo("orphans=2");
        });
    }
}

package com.bank.migration.dataonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.validate.ValidationResult;
import com.bank.migration.validate.ValidationStatus;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class TargetForeignKeyValidatorTest {
    @Mock JdbcTemplate targetJdbc;
    @Mock IdentifierRenderer targetRenderer;
    @Mock TargetForeignKeyScanner scanner;

    @BeforeEach
    void setUp() {
        lenient().when(targetRenderer.renderQualifiedName(anyString(), anyString())).thenAnswer(inv ->
            inv.getArgument(0, String.class).toLowerCase(Locale.ROOT) + "." + inv.getArgument(1, String.class).toLowerCase(Locale.ROOT)
        );
        lenient().when(targetRenderer.render(anyString())).thenAnswer(inv -> inv.getArgument(0, String.class).toLowerCase(Locale.ROOT));
    }

    @Test
    void passesWhenNoTargetForeignKeyOrphansExist() {
        TargetForeignKeyMetadata fk = new TargetForeignKeyMetadata(
            "fk_account_customer",
            "bank_core",
            "ACCOUNT",
            List.of("CUSTOMER_ID"),
            "bank_core",
            "CUSTOMER",
            List.of("ID")
        );
        when(scanner.scan("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"))).thenReturn(List.of(fk));
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        TargetForeignKeyValidator validator = new TargetForeignKeyValidator(targetJdbc, targetRenderer, scanner);

        List<ValidationResult> results = validator.validate("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.name()).isEqualTo("target-foreign-key-orphans");
            assertThat(result.status()).isEqualTo(ValidationStatus.PASS);
            assertThat(result.objectName()).isEqualTo("ACCOUNT.fk_account_customer");
            assertThat(result.message()).isEqualTo("orphans=0");
        });
    }

    @Test
    void failsWhenTargetForeignKeyOrphansExist() {
        TargetForeignKeyMetadata fk = new TargetForeignKeyMetadata(
            "fk_account_customer",
            "bank_core",
            "ACCOUNT",
            List.of("CUSTOMER_ID"),
            "bank_core",
            "CUSTOMER",
            List.of("ID")
        );
        when(scanner.scan("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"))).thenReturn(List.of(fk));
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L);

        TargetForeignKeyValidator validator = new TargetForeignKeyValidator(targetJdbc, targetRenderer, scanner);

        List<ValidationResult> results = validator.validate("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.name()).isEqualTo("target-foreign-key-orphans");
            assertThat(result.status()).isEqualTo(ValidationStatus.FAIL);
            assertThat(result.message()).isEqualTo("orphans=3");
        });
    }

    @Test
    void validatesMigratedChildReferencingExternalOrRetainedParent() {
        TargetForeignKeyMetadata fk = new TargetForeignKeyMetadata(
            "fk_acc_ext_cust",
            "bank_core",
            "ACCOUNT",
            List.of("CUSTOMER_ID"),
            "external_schema",
            "CUSTOMER",
            List.of("ID")
        );
        // Only child table "ACCOUNT" is in the included set. Parent "CUSTOMER" is outside.
        when(scanner.scan("BANK_CORE", Set.of("ACCOUNT"))).thenReturn(List.of(fk));
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        TargetForeignKeyValidator validator = new TargetForeignKeyValidator(targetJdbc, targetRenderer, scanner);
        validator.validate("BANK_CORE", Set.of("ACCOUNT"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(targetJdbc).queryForObject(sqlCaptor.capture(), eq(Long.class));

        assertThat(sqlCaptor.getValue()).contains("bank_core.account c")
            .contains("external_schema.customer p");
    }

    @Test
    void validatesCrossSchemaSameNameParentAgainstCorrectSchema() {
        TargetForeignKeyMetadata fk = new TargetForeignKeyMetadata(
            "fk_acc_shared",
            "bank_core",
            "ACCOUNT",
            List.of("PARENT_ID"),
            "other_schema",
            "PARENT_TABLE",
            List.of("ID")
        );
        when(scanner.scan("BANK_CORE", Set.of("ACCOUNT"))).thenReturn(List.of(fk));
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        TargetForeignKeyValidator validator = new TargetForeignKeyValidator(targetJdbc, targetRenderer, scanner);
        validator.validate("BANK_CORE", Set.of("ACCOUNT"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(targetJdbc).queryForObject(sqlCaptor.capture(), eq(Long.class));

        // It must join other_schema.parent_table, NOT bank_core.parent_table
        assertThat(sqlCaptor.getValue()).contains("other_schema.parent_table p")
            .doesNotContain("bank_core.parent_table p");
    }

    @Test
    void validateCompositeForeignKeyWithMatchFullAndPartialNullIsInvalid() {
        TargetForeignKeyMetadata fk = new TargetForeignKeyMetadata(
            "fk_account_customer",
            "bank_core",
            "ACCOUNT",
            List.of("CUSTOMER_ID", "BRANCH_ID"),
            "bank_core",
            "CUSTOMER",
            List.of("ID", "BRANCH_ID"),
            "f" // MATCH FULL
        );
        when(scanner.scan("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"))).thenReturn(List.of(fk));
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2L);

        TargetForeignKeyValidator validator = new TargetForeignKeyValidator(targetJdbc, targetRenderer, scanner);
        List<ValidationResult> results = validator.validate("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.name()).isEqualTo("target-foreign-key-orphans");
            assertThat(result.status()).isEqualTo(ValidationStatus.FAIL);
            assertThat(result.message()).isEqualTo("orphans=2");
        });

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(targetJdbc).queryForObject(sqlCaptor.capture(), eq(Long.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("((c.customer_id is null or c.branch_id is null) and (c.customer_id is not null or c.branch_id is not null))");
        assertThat(sql).contains("or (c.customer_id is not null and c.branch_id is not null and p.id is null)");
    }

    @Test
    void validateCompositeForeignKeyWithMatchSimpleKeepsCurrentSemantics() {
        TargetForeignKeyMetadata fk = new TargetForeignKeyMetadata(
            "fk_account_customer",
            "bank_core",
            "ACCOUNT",
            List.of("CUSTOMER_ID", "BRANCH_ID"),
            "bank_core",
            "CUSTOMER",
            List.of("ID", "BRANCH_ID"),
            "s" // MATCH SIMPLE
        );
        when(scanner.scan("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"))).thenReturn(List.of(fk));
        when(targetJdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);

        TargetForeignKeyValidator validator = new TargetForeignKeyValidator(targetJdbc, targetRenderer, scanner);
        validator.validate("BANK_CORE", Set.of("ACCOUNT", "CUSTOMER"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(targetJdbc).queryForObject(sqlCaptor.capture(), eq(Long.class));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("where c.customer_id is not null and c.branch_id is not null and p.id is null");
        assertThat(sql).doesNotContain("is null or");
    }
}

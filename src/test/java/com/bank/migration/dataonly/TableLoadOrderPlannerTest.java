package com.bank.migration.dataonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.migration.config.DataOnlyForeignKeyHandling;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableLoadOrderPlannerTest {
    private final TableLoadOrderPlanner planner = new TableLoadOrderPlanner();

    @Test
    void ordersParentBeforeChildForForeignKeys() {
        TableMetadata parent = table("CUSTOMER", List.of(pk("PK_CUSTOMER", "CUSTOMER_ID")));
        TableMetadata child = table("ACCOUNT", List.of(
            pk("PK_ACCOUNT", "ACCOUNT_ID"),
            fk("FK_ACCOUNT_CUSTOMER", "CUSTOMER_ID", "CUSTOMER", "CUSTOMER_ID")
        ));
        MigrationManifest manifest = new MigrationManifest("run-1", "BANK_CORE", List.of(child, parent), List.of());

        List<TableMetadata> ordered = planner.order(manifest, DataOnlyForeignKeyHandling.ORDER_ONLY);

        assertThat(ordered).extracting(TableMetadata::name).containsExactly("CUSTOMER", "ACCOUNT");
    }

    @Test
    void ignoresSelfReferencingForeignKey() {
        TableMetadata employee = table("EMPLOYEE", List.of(
            pk("PK_EMPLOYEE", "EMP_ID"),
            fk("FK_EMPLOYEE_MANAGER", "MANAGER_ID", "EMPLOYEE", "EMP_ID")
        ));
        MigrationManifest manifest = new MigrationManifest("run-1", "BANK_CORE", List.of(employee), List.of());

        List<TableMetadata> ordered = planner.order(manifest, DataOnlyForeignKeyHandling.ORDER_ONLY);

        assertThat(ordered).extracting(TableMetadata::name).containsExactly("EMPLOYEE");
    }

    @Test
    void multipleForeignKeysToSameParentDoNotCauseCycle() {
        TableMetadata parent = table("CUSTOMER", List.of(pk("PK_CUSTOMER", "CUSTOMER_ID")));
        TableMetadata child = table("ACCOUNT", List.of(
            pk("PK_ACCOUNT", "ACCOUNT_ID"),
            fk("FK_ACCOUNT_PRIMARY_OWNER", "PRIMARY_OWNER_ID", "CUSTOMER", "CUSTOMER_ID"),
            fk("FK_ACCOUNT_JOINT_OWNER", "JOINT_OWNER_ID", "CUSTOMER", "CUSTOMER_ID")
        ));
        MigrationManifest manifest = new MigrationManifest("run-1", "BANK_CORE", List.of(child, parent), List.of());

        List<TableMetadata> ordered = planner.order(manifest, DataOnlyForeignKeyHandling.ORDER_ONLY);

        assertThat(ordered).extracting(TableMetadata::name).containsExactly("CUSTOMER", "ACCOUNT");
    }

    @Test
    void orderOnlyBlocksCircularForeignKeys() {
        TableMetadata a = table("A", List.of(pk("PK_A", "ID"), fk("FK_A_B", "B_ID", "B", "ID")));
        TableMetadata b = table("B", List.of(pk("PK_B", "ID"), fk("FK_B_A", "A_ID", "A", "ID")));
        MigrationManifest manifest = new MigrationManifest("run-1", "BANK_CORE", List.of(a, b), List.of());

        assertThatThrownBy(() -> planner.order(manifest, DataOnlyForeignKeyHandling.ORDER_ONLY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Circular foreign key dependency");
    }

    @Test
    void disableReenableAllowsCircularForeignKeys() {
        TableMetadata a = table("A", List.of(pk("PK_A", "ID"), fk("FK_A_B", "B_ID", "B", "ID")));
        TableMetadata b = table("B", List.of(pk("PK_B", "ID"), fk("FK_B_A", "A_ID", "A", "ID")));
        MigrationManifest manifest = new MigrationManifest("run-1", "BANK_CORE", List.of(a, b), List.of());

        List<TableMetadata> ordered = planner.order(manifest, DataOnlyForeignKeyHandling.DISABLE_REENABLE);

        assertThat(ordered).extracting(TableMetadata::name).containsExactly("A", "B");
    }

    private static TableMetadata table(String name, List<KeyMetadata> keys) {
        return new TableMetadata("BANK_CORE", name, ObjectStatus.READY, List.of(), keys, List.of());
    }

    private static KeyMetadata pk(String name, String column) {
        return new KeyMetadata(name, "PRIMARY_KEY", List.of(column), null, null);
    }

    private static KeyMetadata fk(String name, String column, String referencedTable, String referencedColumn) {
        return new KeyMetadata(name, "FOREIGN_KEY", List.of(column), referencedTable, List.of(referencedColumn));
    }
}

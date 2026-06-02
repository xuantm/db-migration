package com.bank.migration.ddl;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableDdlPlannerTest {
    private final TableDdlPlanner planner = new TableDdlPlanner(new OracleToGaussTypeMapper());

    @Test
    void createsTableAndDeferredIndexStatements() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 19, 0, false, null),
                new ColumnMetadata("ACCOUNT_NO", "VARCHAR2", 32, null, false, null)
            ),
            List.of(
                new KeyMetadata("PK_ACCOUNT", "PRIMARY_KEY", List.of("ID"), null, null)
            ),
            List.of(
                new IndexMetadata("IX_ACCOUNT_NO", false, List.of("ACCOUNT_NO"))
            )
        );

        List<DdlStatement> statements = planner.plan("TARGET_SCHEMA", table);

        assertThat(statements).extracting(DdlStatement::phase).containsExactly("TABLE", "CONSTRAINT", "INDEX");
        assertThat(statements.get(0).sql()).isEqualTo("""
            create table target_schema.account (
              id bigint not null,
              account_no varchar(32) not null
            )""");
        assertThat(statements.get(1).sql()).isEqualTo(
            "alter table target_schema.account add constraint pk_account primary key (id)"
        );
        assertThat(statements.get(2).sql()).isEqualTo(
            "create index ix_account_no on target_schema.account (account_no)"
        );
    }
}

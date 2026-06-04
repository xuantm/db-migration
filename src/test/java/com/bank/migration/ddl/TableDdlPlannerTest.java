package com.bank.migration.ddl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.IndexMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.types.OracleToGaussTypeMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableDdlPlannerTest {
    private final TableDdlPlanner planner = new TableDdlPlanner(
        new OracleToGaussTypeMapper(),
        new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.QUOTE))
    );

    @Test
    void createsTableAndDeferredIndexStatements() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
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
        assertThat(statements).extracting(DdlStatement::objectName).containsExactly("ACCOUNT", "PK_ACCOUNT", "IX_ACCOUNT_NO");
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

    @Test
    void createsUniqueAndForeignKeyStatementsInOrder() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                new ColumnMetadata("ACCOUNT_NO", "VARCHAR2", 32, null, false, null),
                new ColumnMetadata("CUSTOMER_ID", "NUMBER", 18, 0, false, null)
            ),
            List.of(
                new KeyMetadata("PK_ACCOUNT", "PRIMARY_KEY", List.of("ID"), null, null),
                new KeyMetadata("UK_ACCOUNT_NO", "UNIQUE", List.of("ACCOUNT_NO"), null, null),
                new KeyMetadata("FK_ACCOUNT_CUSTOMER", "FOREIGN_KEY", List.of("CUSTOMER_ID"), "CUSTOMER", List.of("ID"))
            ),
            List.of(
                new IndexMetadata("IX_ACCOUNT_NO", false, List.of("ACCOUNT_NO"))
            )
        );

        List<DdlStatement> statements = planner.plan("TARGET_SCHEMA", table);

        assertThat(statements).extracting(DdlStatement::phase).containsExactly("TABLE", "CONSTRAINT", "CONSTRAINT", "INDEX", "FOREIGN_KEY");
        assertThat(statements).extracting(DdlStatement::objectName).containsExactly(
            "ACCOUNT",
            "PK_ACCOUNT",
            "UK_ACCOUNT_NO",
            "IX_ACCOUNT_NO",
            "FK_ACCOUNT_CUSTOMER"
        );
        assertThat(statements.get(2).sql()).isEqualTo(
            "alter table target_schema.account add constraint uk_account_no unique (account_no)"
        );
        assertThat(statements.get(4).sql()).isEqualTo(
            "alter table target_schema.account add constraint fk_account_customer foreign key (customer_id) references target_schema.customer (id)"
        );
    }

    @Test
    void rejectsUnsupportedColumnsBeforeEmittingSql() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.WARNING,
            List.of(new ColumnMetadata("PAYLOAD", "XMLTYPE", null, null, true, null)),
            List.of(),
            List.of()
        );

        assertThatThrownBy(() -> planner.plan("TARGET_SCHEMA", table))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PAYLOAD")
            .hasMessageContaining("Unsupported Oracle type XMLTYPE");
    }

    @Test
    void rejectsTablesWithoutColumns() {
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.WARNING, List.of(), List.of(), List.of());

        assertThatThrownBy(() -> planner.plan("TARGET_SCHEMA", table))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ACCOUNT");
    }

    @Test
    void plansDdlForReservedWordsUnderQuotePolicy() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "IBS_CUSERAPPLIMIT",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                new ColumnMetadata("LIMIT", "NUMBER", 18, 0, false, null)
            ),
            List.of(
                new KeyMetadata("PK_LIMIT", "PRIMARY_KEY", List.of("ID"), null, null)
            ),
            List.of()
        );

        IdentifierRenderer targetRenderer = new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.QUOTE));
        TableDdlPlanner plannerQuote = new TableDdlPlanner(new OracleToGaussTypeMapper(), targetRenderer);
        List<DdlStatement> statements = plannerQuote.plan("TARGET_SCHEMA", table);

        assertThat(statements.get(0).sql()).isEqualTo("""
            create table target_schema.ibs_cuserapplimit (
              id bigint not null,
              "LIMIT" bigint not null
            )""");
    }

    @Test
    void plansDdlForReservedWordsUnderRenamePolicy() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "IBS_CUSERAPPLIMIT",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 18, 0, false, null),
                new ColumnMetadata("LIMIT", "NUMBER", 18, 0, false, null)
            ),
            List.of(
                new KeyMetadata("PK_LIMIT", "PRIMARY_KEY", List.of("ID"), null, null)
            ),
            List.of()
        );

        IdentifierRenderer targetRenderer = new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.RENAME));
        TableDdlPlanner plannerRename = new TableDdlPlanner(new OracleToGaussTypeMapper(), targetRenderer);
        List<DdlStatement> statements = plannerRename.plan("TARGET_SCHEMA", table);

        assertThat(statements.get(0).sql()).isEqualTo("""
            create table target_schema.ibs_cuserapplimit (
              id bigint not null,
              limit_ bigint not null
            )""");
    }
}

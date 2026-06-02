package com.bank.migration.ddl;

import static org.mockito.Mockito.inOrder;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DdlApplierTest {
    @Mock JdbcTemplate targetJdbc;

    @Test
    void appliesStatementsInOrder() {
        DdlApplier applier = new DdlApplier(targetJdbc);

        applier.apply(List.of(
            new DdlStatement("TABLE", "ACCOUNT", "create table bank_core.account (id bigint)"),
            new DdlStatement("INDEX", "IX_ACCOUNT", "create index ix_account on bank_core.account (id)")
        ));

        InOrder order = inOrder(targetJdbc);
        order.verify(targetJdbc).execute("create table bank_core.account (id bigint)");
        order.verify(targetJdbc).execute("create index ix_account on bank_core.account (id)");
    }
}

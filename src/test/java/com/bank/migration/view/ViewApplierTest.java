package com.bank.migration.view;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ViewApplierTest {
    @Mock JdbcTemplate targetJdbc;

    private ViewApplier applierWithPolicy(IdentifierMappingPolicy policy) {
        return new ViewApplier(targetJdbc, new IdentifierRenderer(new GaussDialect(policy)));
    }

    @Test
    void appliesOnlyReadyViews() {
        ViewApplier applier = applierWithPolicy(IdentifierMappingPolicy.QUOTE);
        ViewPlan ready = new ViewPlan("VW_ACCOUNT", ObjectStatus.READY, "create view bank_core.vw_account as select 1", List.of());
        ViewPlan review = new ViewPlan("VW_BAD", ObjectStatus.NEEDS_REVIEW, "create view bank_core.vw_bad as select 1", List.of("review"));

        applier.applyReadyViews("bank_core", List.of(ready, review));

        verify(targetJdbc).execute("SET search_path TO bank_core, public");
        verify(targetJdbc).execute("create view bank_core.vw_account as select 1");
        verify(targetJdbc, never()).execute("create view bank_core.vw_bad as select 1");
    }

    @Test
    void setsSearchPathToUserUnderQuotePolicy() {
        ViewApplier applier = applierWithPolicy(IdentifierMappingPolicy.QUOTE);
        ViewPlan ready = new ViewPlan("VW_ACCOUNT", ObjectStatus.READY, "create view \"USER\".vw_account as select 1", List.of());

        applier.applyReadyViews("USER", List.of(ready));

        verify(targetJdbc).execute("SET search_path TO \"USER\", public");
        verify(targetJdbc).execute("create view \"USER\".vw_account as select 1");
    }
}

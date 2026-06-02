package com.bank.migration.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.ViewMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;

class ViewPlannerTest {
    @Test
    void createsCompatibleViewSqlWithoutRewritingBodyCase() {
        ViewPlanner planner = new ViewPlanner();
        ViewMetadata view = new ViewMetadata(
            "BANK_CORE",
            "VW_ACCOUNT",
            ObjectStatus.READY,
            "select ID, 'ACTIVE' as STATUS from ACCOUNT",
            List.of("ACCOUNT"),
            List.of()
        );

        ViewPlan plan = planner.plan("bank_core", view);

        assertThat(plan.status()).isEqualTo(ObjectStatus.READY);
        assertThat(plan.sql()).isEqualTo("create or replace view bank_core.vw_account as select ID, 'ACTIVE' as STATUS from ACCOUNT");
    }

    @Test
    void marksOracleSpecificViewForReview() {
        ViewPlanner planner = new ViewPlanner();
        ViewMetadata view = new ViewMetadata(
            "BANK_CORE",
            "VW_BAD",
            ObjectStatus.READY,
            "select sys_context('USERENV','SESSION_USER') from dual",
            List.of(),
            List.of()
        );

        ViewPlan plan = planner.plan("bank_core", view);

        assertThat(plan.status()).isEqualTo(ObjectStatus.NEEDS_REVIEW);
        assertThat(plan.notes()).contains("Oracle-specific SQL detected: sys_context");
        assertThat(plan.notes()).contains("Oracle-specific SQL detected: dual");
    }
}

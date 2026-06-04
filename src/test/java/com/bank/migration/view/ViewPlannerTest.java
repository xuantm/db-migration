package com.bank.migration.view;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.ViewMetadata;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ViewPlannerTest {

    private ViewPlanner plannerWithPolicy(IdentifierMappingPolicy policy) {
        return new ViewPlanner(new IdentifierRenderer(new GaussDialect(policy)));
    }

    @Test
    void createsCompatibleViewSqlWithoutRewritingBodyCase() {
        ViewPlanner planner = plannerWithPolicy(IdentifierMappingPolicy.QUOTE);
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
    void createsViewSqlForReservedUserUnderQuotePolicy() {
        ViewPlanner planner = plannerWithPolicy(IdentifierMappingPolicy.QUOTE);
        ViewMetadata view = new ViewMetadata(
            "USER",
            "VW_ACCOUNT",
            ObjectStatus.READY,
            "select ID from ACCOUNT",
            List.of("ACCOUNT"),
            List.of()
        );

        ViewPlan plan = planner.plan("USER", view);

        assertThat(plan.status()).isEqualTo(ObjectStatus.READY);
        assertThat(plan.sql()).isEqualTo("create or replace view \"USER\".vw_account as select ID from ACCOUNT");
    }

    @Test
    void marksOracleSpecificViewForReview() {
        ViewPlanner planner = plannerWithPolicy(IdentifierMappingPolicy.QUOTE);
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

    @Test
    void marksLegacyJoinAndTranslateForReview() {
        ViewPlanner planner = plannerWithPolicy(IdentifierMappingPolicy.QUOTE);
        ViewMetadata view = new ViewMetadata(
            "BANK_CORE",
            "VW_LEGACY",
            ObjectStatus.READY,
            "select a.id from tab_a a, tab_b b where a.id = b.id (+) and translate(a.name using nchar_cs) = 'test'",
            List.of(),
            List.of()
        );

        ViewPlan plan = planner.plan("bank_core", view);

        assertThat(plan.status()).isEqualTo(ObjectStatus.NEEDS_REVIEW);
        assertThat(plan.notes()).contains("Oracle-specific outer join syntax detected: (+)");
        assertThat(plan.notes()).contains("Oracle-specific character set translation detected: nchar_cs");
    }

    @Test
    void marksJoinDualForReview() {
        ViewPlanner planner = plannerWithPolicy(IdentifierMappingPolicy.QUOTE);
        ViewMetadata view = new ViewMetadata(
            "BANK_CORE",
            "VW_JOIN_DUAL",
            ObjectStatus.READY,
            "select a.id from tab_a a join dual d on 1=1",
            List.of(),
            List.of()
        );

        ViewPlan plan = planner.plan("bank_core", view);

        assertThat(plan.status()).isEqualTo(ObjectStatus.NEEDS_REVIEW);
        assertThat(plan.notes()).contains("Oracle-specific SQL detected: dual");
    }

    @Test
    void preservesWhitespaceInsideLiteralsAndFormatting() {
        ViewPlanner planner = plannerWithPolicy(IdentifierMappingPolicy.QUOTE);
        ViewMetadata view = new ViewMetadata(
            "BANK_CORE",
            "VW_SPACEY",
            ObjectStatus.READY,
            "select ID,\n    'ACTIVE   STATUS' as STATUS\nfrom ACCOUNT",
            List.of("ACCOUNT"),
            List.of()
        );

        ViewPlan plan = planner.plan("bank_core", view);

        assertThat(plan.status()).isEqualTo(ObjectStatus.READY);
        assertThat(plan.sql()).isEqualTo("create or replace view bank_core.vw_spacey as select ID,\n    'ACTIVE   STATUS' as STATUS\nfrom ACCOUNT");
    }
}

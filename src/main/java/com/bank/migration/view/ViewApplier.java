package com.bank.migration.view;

import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ViewApplier {
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;

    public ViewApplier(
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
    }

    public void applyReadyViews(String targetSchema, List<ViewPlan> plans) {
        boolean hasReady = plans.stream().anyMatch(plan -> plan.status() == ObjectStatus.READY);
        if (!hasReady) {
            return;
        }
        targetJdbc.execute("SET search_path TO " + targetRenderer.render(targetSchema) + ", public");
        for (ViewPlan plan : plans) {
            if (plan.status() == ObjectStatus.READY) {
                targetJdbc.execute(plan.sql());
            }
        }
    }
}

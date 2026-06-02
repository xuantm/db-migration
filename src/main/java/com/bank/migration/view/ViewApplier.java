package com.bank.migration.view;

import com.bank.migration.domain.ObjectStatus;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ViewApplier {
    private final JdbcTemplate targetJdbc;

    public ViewApplier(@Qualifier("targetJdbc") JdbcTemplate targetJdbc) {
        this.targetJdbc = targetJdbc;
    }

    public void applyReadyViews(List<ViewPlan> plans) {
        for (ViewPlan plan : plans) {
            if (plan.status() == ObjectStatus.READY) {
                targetJdbc.execute(plan.sql());
            }
        }
    }
}

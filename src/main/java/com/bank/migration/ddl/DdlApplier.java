package com.bank.migration.ddl;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DdlApplier {
    private final JdbcTemplate targetJdbc;

    public DdlApplier(@Qualifier("targetJdbc") JdbcTemplate targetJdbc) {
        this.targetJdbc = targetJdbc;
    }

    public void apply(List<DdlStatement> statements) {
        for (DdlStatement statement : statements) {
            targetJdbc.execute(statement.sql());
        }
    }
}

package com.bank.migration.view;

import com.bank.migration.domain.ObjectStatus;
import java.util.List;

public record ViewPlan(String name, ObjectStatus status, String sql, List<String> notes) {
    public ViewPlan {
        notes = List.copyOf(notes == null ? List.of() : notes);
    }
}

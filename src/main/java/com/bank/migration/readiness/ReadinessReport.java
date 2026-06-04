package com.bank.migration.readiness;

import java.util.List;

public record ReadinessReport(
    List<ReadinessFinding> findings,
    boolean shouldStop
) {
    public ReadinessReport {
        findings = List.copyOf(findings == null ? List.of() : findings);
    }
}

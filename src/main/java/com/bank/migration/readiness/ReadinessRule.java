package com.bank.migration.readiness;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.domain.MigrationManifest;
import java.util.List;

public interface ReadinessRule {
    List<ReadinessFinding> evaluate(MigrationManifest manifest, MigrationProperties properties);
}

package com.bank.migration.validate;

import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ValidationCoordinator {
    private final RowCountValidator rowCountValidator;
    private final DuplicateKeyValidator duplicateKeyValidator;
    private final ForeignKeyValidator foreignKeyValidator;
    private final ChecksumValidator checksumValidator;
    private final SampleRowValidator sampleRowValidator;
    private final JdbcTemplate targetJdbc;

    public ValidationCoordinator(
        RowCountValidator rowCountValidator,
        DuplicateKeyValidator duplicateKeyValidator,
        ForeignKeyValidator foreignKeyValidator,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc
    ) {
        this(rowCountValidator, duplicateKeyValidator, foreignKeyValidator, null, null, targetJdbc);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ValidationCoordinator(
        RowCountValidator rowCountValidator,
        DuplicateKeyValidator duplicateKeyValidator,
        ForeignKeyValidator foreignKeyValidator,
        ChecksumValidator checksumValidator,
        SampleRowValidator sampleRowValidator,
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc
    ) {
        this.rowCountValidator = rowCountValidator;
        this.duplicateKeyValidator = duplicateKeyValidator;
        this.foreignKeyValidator = foreignKeyValidator;
        this.checksumValidator = checksumValidator;
        this.sampleRowValidator = sampleRowValidator;
        this.targetJdbc = targetJdbc;
    }

    public List<ValidationResult> validate(MigrationManifest manifest, String targetSchema) {
        List<ValidationResult> results = new ArrayList<>();
        for (TableMetadata table : manifest.tables()) {
            if (table.status() == ObjectStatus.EXCLUDED) {
                continue;
            }
            results.add(rowCountValidator.validate(table, targetSchema));
            results.addAll(validateUniqueKeys(table, targetSchema));
            results.addAll(validateForeignKeys(table, targetSchema));
            if (checksumValidator != null) {
                results.add(checksumValidator.validate(table, targetSchema));
            }
            if (sampleRowValidator != null) {
                results.add(sampleRowValidator.validate(table, targetSchema));
            }
        }
        return List.copyOf(results);
    }

    private List<ValidationResult> validateUniqueKeys(TableMetadata table, String targetSchema) {
        List<ValidationResult> results = new ArrayList<>();
        for (KeyMetadata key : table.keys()) {
            if (!"PRIMARY_KEY".equalsIgnoreCase(key.type()) && !"UNIQUE".equalsIgnoreCase(key.type())) {
                continue;
            }
            String duplicateSql = "select count(*) from (" + duplicateKeyValidator.duplicateSql(targetSchema, table, key) + ") duplicate_groups";
            Long duplicates = targetJdbc.queryForObject(duplicateSql, Long.class);
            long duplicateCount = duplicates == null ? 0L : duplicates;
            results.add(new ValidationResult(
                "duplicate-key",
                duplicateCount == 0L ? ValidationStatus.PASS : ValidationStatus.FAIL,
                table.name() + "." + key.name(),
                "duplicateGroups=" + duplicateCount
            ));
        }
        return results;
    }

    private List<ValidationResult> validateForeignKeys(TableMetadata table, String targetSchema) {
        List<ValidationResult> results = new ArrayList<>();
        for (KeyMetadata key : table.keys()) {
            if (!"FOREIGN_KEY".equalsIgnoreCase(key.type())) {
                continue;
            }
            Long orphans = targetJdbc.queryForObject(foreignKeyValidator.orphanSql(targetSchema, table, key), Long.class);
            long orphanCount = orphans == null ? 0L : orphans;
            results.add(new ValidationResult(
                "foreign-key-orphans",
                orphanCount == 0L ? ValidationStatus.PASS : ValidationStatus.FAIL,
                table.name() + "." + key.name(),
                "orphans=" + orphanCount
            ));
        }
        return results;
    }
}

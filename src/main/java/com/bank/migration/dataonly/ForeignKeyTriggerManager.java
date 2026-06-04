package com.bank.migration.dataonly;

import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierRenderer;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ForeignKeyTriggerManager {
    private final JdbcTemplate targetJdbc;
    private final IdentifierRenderer targetRenderer;

    public ForeignKeyTriggerManager(
        @Qualifier("targetJdbc") JdbcTemplate targetJdbc,
        @Qualifier("targetIdentifierRenderer") IdentifierRenderer targetRenderer
    ) {
        this.targetJdbc = targetJdbc;
        this.targetRenderer = targetRenderer;
    }

    public record DisabledTrigger(String qualifiedTableName, String triggerName, String originalState) {
        public DisabledTrigger(String qualifiedTableName, String triggerName) {
            this(qualifiedTableName, triggerName, "O");
        }
    }

    public static class DisableSnapshot {
        private final List<DisabledTrigger> disabledTriggers = new java.util.ArrayList<>();

        public void record(String qualifiedTableName, String triggerName) {
            disabledTriggers.add(new DisabledTrigger(qualifiedTableName, triggerName, "O"));
        }

        public void record(String qualifiedTableName, String triggerName, String originalState) {
            disabledTriggers.add(new DisabledTrigger(qualifiedTableName, triggerName, originalState));
        }

        public List<DisabledTrigger> getDisabledTriggers() {
            return List.copyOf(disabledTriggers);
        }
    }

    public static class TriggerDisableException extends RuntimeException {
        private final DisableSnapshot snapshot;

        public TriggerDisableException(Throwable cause, DisableSnapshot snapshot) {
            super("Failed to disable all foreign key triggers", cause);
            this.snapshot = snapshot;
        }

        public DisableSnapshot getSnapshot() {
            return snapshot;
        }
    }

    public record TriggerInfo(String tgname, String tgenabled) {}

    public DisableSnapshot disableAll(String targetSchema, List<TableMetadata> tables) {
        DisableSnapshot snapshot = new DisableSnapshot();
        String physicalSchema = targetRenderer.physicalName(targetSchema);
        for (TableMetadata table : included(tables)) {
            String physicalTableName = targetRenderer.physicalName(table.name());
            List<TriggerInfo> triggers;
            try {
                triggers = getForeignKeyTriggers(physicalSchema, physicalTableName);
            } catch (Exception ex) {
                throw new TriggerDisableException(ex, snapshot);
            }
            if (triggers.isEmpty()) {
                continue;
            }
            String qualifiedTableName = targetRenderer.renderQualifiedName(targetSchema, table.name());
            for (TriggerInfo trigger : triggers) {
                String quotedTrigger = "\"" + trigger.tgname().replace("\"", "\"\"") + "\"";
                try {
                    targetJdbc.execute("alter table " + qualifiedTableName + " disable trigger " + quotedTrigger);
                } catch (Exception ex) {
                    throw new TriggerDisableException(ex, snapshot);
                }
                snapshot.record(qualifiedTableName, trigger.tgname(), trigger.tgenabled());
            }
        }
        return snapshot;
    }

    public void enableAll(String targetSchema, DisableSnapshot snapshot) {
        if (snapshot == null || snapshot.getDisabledTriggers().isEmpty()) {
            return;
        }
        List<Exception> failures = new java.util.ArrayList<>();
        for (DisabledTrigger triggerRef : snapshot.getDisabledTriggers()) {
            String qualifiedTableName = triggerRef.qualifiedTableName();
            String trigger = triggerRef.triggerName();
            String originalState = triggerRef.originalState();
            String quotedTrigger = "\"" + trigger.replace("\"", "\"\"") + "\"";
            
            String state = originalState != null ? originalState.trim().toUpperCase() : "O";
            String action = switch (state) {
                case "D" -> "disable trigger";
                case "R" -> "enable replica trigger";
                case "A" -> "enable always trigger";
                default -> "enable trigger";
            };
            
            try {
                targetJdbc.execute("alter table " + qualifiedTableName + " " + action + " " + quotedTrigger);
            } catch (Exception ex) {
                failures.add(ex);
            }
        }
        if (!failures.isEmpty()) {
            RuntimeException ex = new RuntimeException("Failed to re-enable one or more foreign key triggers");
            for (Exception failure : failures) {
                ex.addSuppressed(failure);
            }
            throw ex;
        }
    }

    private List<TriggerInfo> getForeignKeyTriggers(String physicalSchema, String tableName) {
        String sql = """
            SELECT t.tgname, t.tgenabled
            FROM pg_catalog.pg_trigger t
            JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid
            JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = ?
              AND c.relname = ?
              AND t.tgconstraint <> 0
              AND EXISTS (
                  SELECT 1 FROM pg_catalog.pg_constraint con
                  WHERE con.oid = t.tgconstraint
                    AND con.contype = 'f'
                    AND con.conrelid = t.tgrelid
              )
            """;
        return targetJdbc.query(sql, (rs, rowNum) -> new TriggerInfo(rs.getString("tgname"), rs.getString("tgenabled")), physicalSchema, tableName);
    }

    private static List<TableMetadata> included(List<TableMetadata> tables) {
        return tables.stream()
            .filter(table -> table.status() != ObjectStatus.EXCLUDED)
            .toList();
    }
}

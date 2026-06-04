package com.bank.migration.dataonly;

import com.bank.migration.config.DataOnlyForeignKeyHandling;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TableLoadOrderPlanner {
    public List<TableMetadata> order(MigrationManifest manifest, DataOnlyForeignKeyHandling handling) {
        List<TableMetadata> included = manifest.tables().stream()
            .filter(table -> table.status() != ObjectStatus.EXCLUDED)
            .toList();
        if (handling != DataOnlyForeignKeyHandling.ORDER_ONLY) {
            return included;
        }
        return topologicalOrder(included);
    }

    private List<TableMetadata> topologicalOrder(List<TableMetadata> tables) {
        Map<String, TableMetadata> byName = new LinkedHashMap<>();
        for (TableMetadata table : tables) {
            byName.put(key(table.name()), table);
        }

        Map<String, List<String>> childrenByParent = new HashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (TableMetadata table : tables) {
            inDegree.put(key(table.name()), 0);
        }

        for (TableMetadata child : tables) {
            String childName = key(child.name());
            for (KeyMetadata fk : child.keys()) {
                if (!"FOREIGN_KEY".equalsIgnoreCase(fk.type()) || fk.referencedTable() == null) {
                    continue;
                }
                String parentName = key(fk.referencedTable());
                if (parentName.equals(childName) || !byName.containsKey(parentName)) {
                    continue;
                }
                childrenByParent.computeIfAbsent(parentName, ignored -> new ArrayList<>()).add(childName);
                inDegree.put(childName, inDegree.get(childName) + 1);
            }
        }

        ArrayDeque<String> ready = new ArrayDeque<>();
        inDegree.forEach((table, degree) -> {
            if (degree == 0) {
                ready.add(table);
            }
        });

        List<TableMetadata> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String parent = ready.removeFirst();
            ordered.add(byName.get(parent));
            for (String child : childrenByParent.getOrDefault(parent, List.of())) {
                int newDegree = inDegree.get(child) - 1;
                inDegree.put(child, newDegree);
                if (newDegree == 0) {
                    ready.add(child);
                }
            }
        }

        if (ordered.size() != tables.size()) {
            List<String> cycleTables = inDegree.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .toList();
            throw new IllegalStateException("Circular foreign key dependency among tables: " + String.join(", ", cycleTables));
        }
        return List.copyOf(ordered);
    }

    private static String key(String name) {
        return name.toUpperCase(Locale.ROOT);
    }
}

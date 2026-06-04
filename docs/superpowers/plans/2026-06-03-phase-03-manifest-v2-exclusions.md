# Phase 03 Manifest V2 Exclusions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add configuration-driven exclusions while preserving excluded objects in the manifest for audit and readiness reporting.

**Architecture:** Manifest V2 should keep all scanned objects and mark excluded ones with status and reason. Pipeline phases must filter excluded objects before DDL, chunking, data copy, validation, and view application.

**Tech Stack:** Java 21 records, Spring Boot configuration binding, JUnit 5.

---

## Files

- Modify: `src/main/java/com/bank/migration/config/MigrationProperties.java`
- Modify: `src/main/java/com/bank/migration/domain/ObjectStatus.java`
- Modify: `src/main/java/com/bank/migration/domain/TableMetadata.java`
- Modify: `src/main/java/com/bank/migration/domain/ViewMetadata.java`
- Create: `src/main/java/com/bank/migration/exclusion/ExclusionConfig.java`
- Create: `src/main/java/com/bank/migration/exclusion/ExclusionResolver.java`
- Modify: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/bank/migration/exclusion/ExclusionResolverTest.java`
- Test: `src/test/java/com/bank/migration/orchestrator/MigrationOrchestratorPipelineTest.java`

## Tasks

- [ ] **Step 1: Write failing property binding tests**

Extend `MigrationPropertiesTest` to bind:

```yaml
migration:
  excluded:
    tables:
      - EBA_ATM
    views:
      - V_AUDIT
    sequences:
      - AUDIT_SEQ
```

Assert matching is case-insensitive.

- [ ] **Step 2: Write failing exclusion resolver tests**

Assert:

```text
EBA_ATM is excluded when config contains eba_atm.
CUSTOMERS is not excluded when absent.
Excluded table keeps object in manifest with ObjectStatus.EXCLUDED.
```

- [ ] **Step 3: Run focused tests and verify failure**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=MigrationPropertiesTest,ExclusionResolverTest test
```

Expected: fail before implementation.

- [ ] **Step 4: Add config model**

Add nested `Excluded` record to `MigrationProperties` with `List<String> tables`, `views`, and `sequences`. Default missing lists to empty collections.

- [ ] **Step 5: Add object state**

Extend `ObjectStatus` with `EXCLUDED` and add exclusion reason fields only where needed. If record signatures become noisy, create a small `ObjectDecision` record instead of overloading every domain object.

- [ ] **Step 6: Filter pipeline phases**

Ensure excluded tables are not passed to `TableDdlPlanner`, `ChunkBoundsService`, `ChunkPlanner`, `TableMigrationTasklet`, or `ValidationCoordinator`.

- [ ] **Step 7: Report FK/view dependencies on excluded objects**

If a non-excluded FK references an excluded table, create a readiness finding with `BLOCKER`. If a view references an excluded table, mark that view `NEEDS_REVIEW`.

- [ ] **Step 8: Run full tests**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/com/bank/migration/config src/main/java/com/bank/migration/domain src/main/java/com/bank/migration/exclusion src/main/java/com/bank/migration/orchestrator src/main/resources/application.yml src/test/java/com/bank/migration
git commit -m "feat: add migration object exclusions"
```

## Acceptance Gate

- Excluded objects remain visible in manifest/report.
- Excluded tables do not generate DDL, chunks, data, constraints, indexes, or validation.
- FK dependencies on excluded objects are not silently ignored.

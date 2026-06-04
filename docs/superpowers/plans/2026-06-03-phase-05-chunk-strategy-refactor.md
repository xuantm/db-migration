# Phase 05 Chunk Strategy Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support numeric primary keys, non-numeric primary keys, composite primary keys, and no-primary-key tables without casting every key to `Long`.

**Architecture:** Replace one-size chunk planning with strategy selection. Each strategy owns its bounds query and predicate SQL for Oracle source data.

**Tech Stack:** Java 21, Spring JDBC, Oracle SQL, JUnit 5.

---

## Files

- Create: `src/main/java/com/bank/migration/chunk/ChunkStrategy.java`
- Create: `src/main/java/com/bank/migration/chunk/ChunkStrategyType.java`
- Create: `src/main/java/com/bank/migration/chunk/ChunkStrategySelector.java`
- Create: `src/main/java/com/bank/migration/chunk/NumericPrimaryKeyChunkStrategy.java`
- Create: `src/main/java/com/bank/migration/chunk/RowNumberChunkStrategy.java`
- Create: `src/main/java/com/bank/migration/chunk/RowIdChunkStrategy.java`
- Modify: `src/main/java/com/bank/migration/chunk/ChunkBoundsService.java`
- Modify: `src/main/java/com/bank/migration/chunk/ChunkPlanner.java`
- Modify: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`
- Test: `src/test/java/com/bank/migration/chunk/ChunkStrategySelectorTest.java`
- Test: `src/test/java/com/bank/migration/chunk/ChunkPlannerTest.java`

## Tasks

- [ ] **Step 1: Write failing strategy selector tests**

Assert:

```text
Single NUMBER PK -> NUMERIC_PRIMARY_KEY
Single VARCHAR2 PK -> ROW_NUMBER
Composite PK -> ROW_NUMBER
No PK -> ROWID
Empty table -> no chunks
```

- [ ] **Step 2: Write failing VARCHAR PK chunk test**

Use table `EBA_CITY` with PK `CITYID VARCHAR2`. Assert generated source predicate does not compare `CITYID >= 1`.

- [ ] **Step 3: Run focused tests and verify failure**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=ChunkStrategySelectorTest,ChunkPlannerTest test
```

Expected: fail because current implementation assumes numeric PK ranges.

- [ ] **Step 4: Implement strategy selector**

Use column metadata and primary-key metadata. Numeric means Oracle `NUMBER` with scale `0` and precision present or null. Non-numeric includes `VARCHAR2`, `CHAR`, `RAW`, and temporal types.

- [ ] **Step 5: Implement numeric strategy**

Keep current min/max numeric range behavior, but isolate it in `NumericPrimaryKeyChunkStrategy`.

- [ ] **Step 6: Implement row-number strategy**

Generate predicates in this form:

```sql
ROWID in (
  select rid
  from (
    select ROWID rid, row_number() over (order by <stable columns>) rn
    from <schema>.<table>
  )
  where rn >= <start> and rn <= <end>
)
```

Use primary-key columns for ordering when present. Use `ROWID` ordering for no stable key.

- [ ] **Step 7: Report chosen strategy**

Ensure `ChunkPlan.strategy()` or equivalent exposes the strategy type and appears in audit/report output.

- [ ] **Step 8: Run full tests**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/com/bank/migration/chunk src/main/java/com/bank/migration/orchestrator src/test/java/com/bank/migration/chunk
git commit -m "feat: add chunk strategy selection"
```

## Acceptance Gate

- VARCHAR primary keys do not use numeric range predicates.
- Composite and no-PK tables get deterministic chunk strategy reporting.
- Existing numeric PK behavior remains covered by tests.

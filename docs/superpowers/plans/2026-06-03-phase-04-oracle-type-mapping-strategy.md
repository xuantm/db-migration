# Phase 04 Oracle Type Mapping Strategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace permissive type fallback with a banking-safe type mapping strategy that reports unsupported or risky Oracle types.

**Architecture:** Keep DDL generation separate from type decision logic. Type mapping should return a structured result with target SQL, review notes, and blocking severity.

**Tech Stack:** Java 21 records, Spring service, JUnit 5 parameterized tests.

---

## Files

- Create: `src/main/java/com/bank/migration/types/TypeMappingResult.java`
- Create: `src/main/java/com/bank/migration/types/TypeRisk.java`
- Create: `src/main/java/com/bank/migration/types/UnsupportedTypePolicy.java`
- Move or replace: `src/main/java/com/bank/migration/ddl/OracleToGaussTypeMapper.java`
- Modify: `src/main/java/com/bank/migration/ddl/TableDdlPlanner.java`
- Modify: `src/main/java/com/bank/migration/readiness/ReadinessEvaluator.java`
- Test: `src/test/java/com/bank/migration/types/OracleToGaussTypeMapperTest.java`

## Tasks

- [ ] **Step 1: Write failing mapping tests**

Add parameterized cases:

```text
NUMBER(4,0) -> smallint, READY
NUMBER(9,0) -> integer, READY
NUMBER(18,0) -> bigint, READY
NUMBER(null,null) -> numeric, REVIEW
VARCHAR2(20) -> varchar(20), READY
DATE -> timestamp, READY
CLOB -> text, REVIEW
BLOB -> bytea, REVIEW
BFILE -> no target type, BLOCKED
LONG -> no target type, BLOCKED
XMLTYPE -> no target type, BLOCKED
SDO_GEOMETRY -> no target type, BLOCKED
```

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=OracleToGaussTypeMapperTest test
```

Expected: fail because current mapper returns `text` for unsupported types.

- [ ] **Step 3: Implement mapping result**

`TypeMappingResult` should include:

```java
String targetSqlType;
TypeRisk risk;
List<String> notes;
boolean canGenerateDdl;
```

- [ ] **Step 4: Refactor mapper**

Unsupported Oracle object and multimedia types must not silently become `text`. Mark them `BLOCKED` unless policy explicitly allows fallback.

- [ ] **Step 5: Wire readiness**

Readiness should convert `TypeRisk.BLOCKED` into `ReadinessSeverity.BLOCKER`. `TypeRisk.REVIEW` should become `MEDIUM` or `HIGH` depending on data-loss risk.

- [ ] **Step 6: Update DDL planner**

DDL planner should throw a clear exception if asked to generate DDL for `canGenerateDdl=false`, including table and column names.

- [ ] **Step 7: Run full tests**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/bank/migration/types src/main/java/com/bank/migration/ddl src/main/java/com/bank/migration/readiness src/test/java/com/bank/migration/types
git commit -m "feat: add safe Oracle type mapping strategy"
```

## Acceptance Gate

- Unsupported Oracle types are never silently mapped to `text`.
- Risky but migratable types appear in readiness reports.
- DDL generation fails clearly when type mapping is blocked.

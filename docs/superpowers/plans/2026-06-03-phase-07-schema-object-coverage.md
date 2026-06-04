# Phase 07 Schema Object Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend Oracle scanning and readiness reporting for enterprise schema objects without migrating out-of-scope procedural objects.

**Architecture:** Scanner should capture object inventory separately from migration-ready tables/views. Readiness should report objects that affect migration safety or require manual follow-up.

**Tech Stack:** Java 21 records, Oracle data dictionary queries, Spring JDBC, JUnit 5.

---

## Files

- Create: `src/main/java/com/bank/migration/domain/SchemaObjectMetadata.java`
- Create: `src/main/java/com/bank/migration/domain/SchemaObjectType.java`
- Modify: `src/main/java/com/bank/migration/domain/MigrationManifest.java`
- Modify: `src/main/java/com/bank/migration/scanner/OracleMetadataScanner.java`
- Modify: `src/main/java/com/bank/migration/readiness/ReadinessEvaluator.java`
- Test: `src/test/java/com/bank/migration/scanner/OracleMetadataScannerTest.java`
- Test: `src/test/java/com/bank/migration/readiness/ReadinessEvaluatorTest.java`

## Tasks

- [ ] **Step 1: Write failing scanner tests**

Mock Oracle dictionary rows for:

```text
SEQUENCE
SYNONYM
MATERIALIZED_VIEW
TRIGGER
PROCEDURE
FUNCTION
PACKAGE
TYPE
CHECK_CONSTRAINT
FUNCTION_BASED_INDEX
BITMAP_INDEX
```

Assert they are present in `MigrationManifest.schemaObjects()`.

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=OracleMetadataScannerTest,ReadinessEvaluatorTest test
```

Expected: fail because schema object inventory does not exist.

- [ ] **Step 3: Add schema object domain model**

Fields:

```java
String owner;
String name;
SchemaObjectType type;
ObjectStatus status;
List<String> notes;
```

- [ ] **Step 4: Add dictionary queries**

Use `ALL_OBJECTS`, `ALL_SEQUENCES`, `ALL_SYNONYMS`, `ALL_TRIGGERS`, `ALL_PROCEDURES`, `ALL_TYPES`, `ALL_CONSTRAINTS`, and `ALL_INDEXES` as needed. Keep queries small and testable.

- [ ] **Step 5: Add readiness rules**

Rules:

```text
Trigger/procedure/function/package/type -> MEDIUM, shouldStop=false, not migrated
Materialized view -> MEDIUM, shouldStop=false, not migrated
Function-based index -> HIGH, shouldStop=false, manual recreation needed
Bitmap index -> MEDIUM, shouldStop=false, target compatibility review
Check constraint -> HIGH if not planned for migration
Sequence -> MEDIUM, shouldStop=false in phase one unless target app requires it
```

- [ ] **Step 6: Keep runtime scope unchanged**

Do not add DDL generation for triggers, procedures, functions, packages, jobs, or materialized views.

- [ ] **Step 7: Run full tests**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/bank/migration/domain src/main/java/com/bank/migration/scanner src/main/java/com/bank/migration/readiness src/test/java/com/bank/migration
git commit -m "feat: report enterprise Oracle schema objects"
```

## Acceptance Gate

- Out-of-scope objects are scanned and reported but not migrated.
- Function-based and bitmap indexes appear in readiness output.
- Phase-one runtime scope remains unchanged.

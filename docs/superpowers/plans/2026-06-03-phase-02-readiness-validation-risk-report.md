# Phase 02 Readiness Validation Risk Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pre-migration readiness phase that detects blockers and manual-review items before DDL or data copy begins.

**Architecture:** Add a `readiness` package that evaluates `MigrationManifest` and produces structured findings. The orchestrator should stop before target schema mutation when a blocking finding exists.

**Tech Stack:** Java 21 records, Spring service components, Jackson reports, JUnit 5.

---

## Files

- Create: `src/main/java/com/bank/migration/readiness/ReadinessSeverity.java`
- Create: `src/main/java/com/bank/migration/readiness/ReadinessFinding.java`
- Create: `src/main/java/com/bank/migration/readiness/ReadinessReport.java`
- Create: `src/main/java/com/bank/migration/readiness/ReadinessRule.java`
- Create: `src/main/java/com/bank/migration/readiness/ReadinessEvaluator.java`
- Modify: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`
- Modify: `src/main/java/com/bank/migration/report/ReportWriter.java`
- Test: `src/test/java/com/bank/migration/readiness/ReadinessEvaluatorTest.java`

## Tasks

- [ ] **Step 1: Write failing readiness tests**

Test these findings:

```text
BFILE column -> BLOCKER, shouldStop=true
VARCHAR primary key -> HIGH, shouldStop=false when non-numeric chunk strategy is not active
Reserved word column LIMIT -> HIGH or BLOCKER based on identifier policy
CLOB column -> MEDIUM, shouldStop=false
No primary key -> MEDIUM, shouldStop=false
```

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=ReadinessEvaluatorTest test
```

Expected: compile failure because readiness classes do not exist.

- [ ] **Step 3: Implement readiness model**

`ReadinessFinding` fields:

```java
String code;
ReadinessSeverity severity;
String objectType;
String objectName;
String columnName;
String description;
String detectionMethod;
String mitigation;
boolean shouldStop;
```

- [ ] **Step 4: Implement evaluator rules**

Rules must inspect manifest tables, columns, keys, indexes, and views. Out-of-scope Oracle objects can be added in phase 07.

- [ ] **Step 5: Wire into orchestrator**

Run readiness immediately after `scanner.scan(...)` and before `targetSchemaService.prepareCleanSchema(...)`. If any finding has `shouldStop=true`, write a fail report and throw `IllegalStateException`.

- [ ] **Step 6: Extend reports**

Add readiness findings to JSON and HTML reports. CSV can be a separate readiness CSV or a section in the existing CSV report, as long as all fields are machine-readable.

- [ ] **Step 7: Run full tests**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass and test count increases.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/bank/migration/readiness src/main/java/com/bank/migration/orchestrator src/main/java/com/bank/migration/report src/test/java/com/bank/migration/readiness
git commit -m "feat: add migration readiness evaluation"
```

## Acceptance Gate

- Blocking readiness findings stop before target schema drop/create.
- Reports show severity, object, detection, mitigation, and stop decision.
- The phase does not migrate triggers, procedures, functions, or packages.

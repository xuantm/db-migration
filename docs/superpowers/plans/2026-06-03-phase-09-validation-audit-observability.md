# Phase 09 Validation Audit Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strengthen validation, audit, and operational reporting so migration results are reviewable for banking sign-off.

**Architecture:** Keep validators independent and aggregate them through `ValidationCoordinator`. Extend audit schema with run and phase status without weakening existing chunk/error tables.

**Tech Stack:** Java 21, Spring JDBC, JSON/CSV/HTML report generation, JUnit 5.

---

## Files

- Modify: `src/main/java/com/bank/migration/validate/ValidationCoordinator.java`
- Create: `src/main/java/com/bank/migration/validate/ChecksumValidator.java`
- Create: `src/main/java/com/bank/migration/validate/SampleRowValidator.java`
- Modify: `src/main/java/com/bank/migration/audit/AuditSchemaService.java`
- Create: `src/main/java/com/bank/migration/audit/RunStatusStore.java`
- Create: `src/main/java/com/bank/migration/audit/PhaseStatusStore.java`
- Modify: `src/main/java/com/bank/migration/report/MigrationReport.java`
- Modify: `src/main/java/com/bank/migration/report/ReportWriter.java`
- Test: `src/test/java/com/bank/migration/validate/ChecksumValidatorTest.java`
- Test: `src/test/java/com/bank/migration/audit/AuditSchemaServiceTest.java`
- Test: `src/test/java/com/bank/migration/report/ReportWriterTest.java`

## Tasks

- [ ] **Step 1: Write failing checksum validator tests**

For a table with deterministic columns, assert source checksum and target checksum match. For mismatched values, assert `ValidationStatus.FAIL`.

- [ ] **Step 2: Write failing audit schema tests**

Assert `ensureAuditSchema()` creates:

```text
migration_audit.runs
migration_audit.phase_status
migration_audit.checkpoints
migration_audit.errors
```

- [ ] **Step 3: Run focused tests and verify failure**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=ChecksumValidatorTest,AuditSchemaServiceTest,ReportWriterTest test
```

Expected: fail before implementation.

- [ ] **Step 4: Implement checksum validation**

Use dialect-specific SQL later if phase 01 provides dialects. For the first implementation, support simple scalar columns and skip LOB columns with a `WARNING` validation result containing the skipped column names.

- [ ] **Step 5: Implement sample row validation**

Compare a configurable sample size per table. Default sample size should be `100`. Use primary key order when available; otherwise use row-number order.

- [ ] **Step 6: Extend audit records**

`runs` should capture run id, source schema, target schema, started_at, finished_at, final_status. `phase_status` should capture phase name, started_at, finished_at, status, rows_processed, message.

- [ ] **Step 7: Extend reports**

JSON, CSV, and HTML must show row count, duplicate, FK orphan, checksum, sample row, readiness, and failed chunk information.

- [ ] **Step 8: Run full tests**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```powershell
git add src/main/java/com/bank/migration/validate src/main/java/com/bank/migration/audit src/main/java/com/bank/migration/report src/test/java/com/bank/migration
git commit -m "feat: strengthen migration validation and audit"
```

## Acceptance Gate

- Validation failure makes final report `FAIL`.
- Audit can show run-level and phase-level progress.
- Reports include enough evidence for post-migration sign-off.

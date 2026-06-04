# Phase 00 Baseline Architecture Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a precise architecture review of the current Oracle to GaussDB migration tool and map known risks to current modules.

**Architecture:** This phase does not change production behavior. It creates a written baseline that future phases can use as source-of-truth for scope, risk, and module ownership.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Spring JDBC, Spring Batch, Oracle JDBC, openGauss JDBC, Markdown docs.

---

## Files

- Inspect: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`
- Inspect: `src/main/java/com/bank/migration/scanner/OracleMetadataScanner.java`
- Inspect: `src/main/java/com/bank/migration/ddl/TableDdlPlanner.java`
- Inspect: `src/main/java/com/bank/migration/ddl/OracleToGaussTypeMapper.java`
- Inspect: `src/main/java/com/bank/migration/chunk/ChunkBoundsService.java`
- Inspect: `src/main/java/com/bank/migration/chunk/ChunkPlanner.java`
- Inspect: `src/main/java/com/bank/migration/load/DataCopyService.java`
- Inspect: `src/main/java/com/bank/migration/validate/ValidationCoordinator.java`
- Inspect: `src/main/java/com/bank/migration/audit/AuditSchemaService.java`
- Create: `docs/architecture/phase-00-current-architecture-review.md`

## Tasks

- [ ] **Step 1: Map current runtime flow**

Read `MigrationOrchestrator.run(...)` and document the exact phase order: preflight, audit schema, scan, clean target, DDL, chunks, data copy, constraints, indexes, views, validation, report.

- [ ] **Step 2: Map module responsibility**

Create a table with columns `Module`, `Current Responsibility`, `Observed Limitation`, `Recommended Owner Phase`.

- [ ] **Step 3: Capture known prompt issues against code**

For each issue from the user prompt, point to the module where it currently appears:

```text
Reserved words -> ddl/load SQL rendering
Unsupported Oracle types -> OracleToGaussTypeMapper
Numeric PK assumption -> ChunkBoundsService and ChunkPlanner
LOB pass-through -> DataCopyService
Dialect mixing -> scanner/ddl/load/validate SQL strings
Exclusions missing -> MigrationProperties and manifest filtering
Manifest cache missing -> scanner/orchestrator/audit
Readiness missing -> preflight/orchestrator/report
```

- [ ] **Step 4: Write risk register**

Add a risk register with fields: `ID`, `Description`, `Severity`, `Detection`, `Mitigation`, `Should Stop Migration`.

- [ ] **Step 5: Verify no code changed**

Run:

```powershell
git status --short
```

Expected: only the new review doc is modified or untracked, plus any existing generated `build/` and `target/` directories.

- [ ] **Step 6: Commit the review doc**

```powershell
git add docs/architecture/phase-00-current-architecture-review.md
git commit -m "docs: add baseline migration architecture review"
```

## Acceptance Gate

- The review identifies module owners for every known issue in the prompt.
- The review explicitly confirms phase-one does not migrate triggers, procedures, functions, packages, jobs, or materialized views.
- No production code changes are included in this phase.

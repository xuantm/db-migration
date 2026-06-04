# Phase 10 E2E Performance Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide repeatable Docker-based end-to-end and performance smoke tests against real Oracle and openGauss-compatible targets.

**Architecture:** Keep E2E infrastructure outside production code. Use Docker Compose and scripts to create databases, generate data, run migration, and collect reports.

**Tech Stack:** Docker, Docker Compose, PowerShell, Oracle Free, Swingbench, openGauss/enmotech image, Java 21 jar.

---

## Files

- Inspect: `docker-compose.yml`
- Inspect: `run-e2e-test.ps1`
- Modify: `README.md`
- Create: `e2e/sql/oracle-edge-schema.sql`
- Create: `e2e/sql/oracle-edge-data.sql`
- Create: `e2e/README.md`
- Create: `e2e/expected/edge-readiness-findings.md`
- Test by running: `run-e2e-test.ps1`

## Tasks

- [ ] **Step 1: Verify Docker DB images**

Use Oracle:

```text
gvenzl/oracle-free:slim
```

Use target image that starts successfully on the machine. If official `opengauss/opengauss` fails with missing `libopenblas.so.0`, document it and use `enmotech/opengauss:6.0.0`.

- [ ] **Step 2: Add edge schema SQL**

Create Oracle schema objects for:

```text
reserved word column LIMIT
VARCHAR2 primary key
composite primary key
table with no primary key
CLOB column
BLOB column
BFILE column for readiness blocker
FK from migrated table to excluded table
normal compatible view
complex view requiring review
```

- [ ] **Step 3: Run Swingbench smoke data**

Use `domgiles/swingbench:latest` and binary `/app/swingbench/bin/oewizard`. Use a simple alphanumeric schema password such as `SoePwd123` to avoid reconnect parsing issues.

- [ ] **Step 4: Run edge readiness test**

Run migration with readiness enabled against edge schema. Expected result: migration stops before target schema mutation when `BFILE` is present and reports a `BLOCKER`.

- [ ] **Step 5: Run happy-path E2E**

Run migration against a compatible subset where blockers are excluded or removed. Expected result: jar exits `0`, target tables exist, row counts match, and report status is `PASS` or `WARNING` only for view review.

- [ ] **Step 6: Capture performance baseline**

Run with at least two settings:

```text
chunk-size=1000, max-parallel-tables=1
chunk-size=5000, max-parallel-tables=2
```

Record rows/sec, chunk duration, failed chunks, report status.

- [ ] **Step 7: Document image issues**

In `e2e/README.md`, separate:

```text
Tool defects
Third-party Docker image defects
Environment constraints such as host port 5432 already occupied
```

- [ ] **Step 8: Run unit tests after script/doc changes**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass.

- [ ] **Step 9: Commit**

```powershell
git add docker-compose.yml run-e2e-test.ps1 README.md e2e
git commit -m "test: add repeatable migration e2e scenarios"
```

## Acceptance Gate

- E2E test can be rerun from a clean Docker environment.
- Edge schema proves readiness blockers stop before destructive target actions.
- Performance baseline is documented with actual measured values.

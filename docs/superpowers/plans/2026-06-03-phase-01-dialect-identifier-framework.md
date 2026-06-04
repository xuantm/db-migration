# Phase 01 Dialect Identifier Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce dialect-specific identifier rendering so Oracle source SQL and GaussDB/PostgreSQL target SQL no longer share unsafe string rendering.

**Architecture:** Add focused `dialect` and `identifier` packages. Existing DDL and data-copy code should call these interfaces instead of concatenating raw identifiers directly.

**Tech Stack:** Java 21, Spring Boot, Spring JDBC, JUnit 5, AssertJ.

---

## Files

- Create: `src/main/java/com/bank/migration/dialect/DatabaseDialect.java`
- Create: `src/main/java/com/bank/migration/dialect/OracleDialect.java`
- Create: `src/main/java/com/bank/migration/dialect/GaussDialect.java`
- Create: `src/main/java/com/bank/migration/identifier/Identifier.java`
- Create: `src/main/java/com/bank/migration/identifier/IdentifierRenderer.java`
- Create: `src/main/java/com/bank/migration/identifier/ReservedWordRegistry.java`
- Create: `src/main/java/com/bank/migration/identifier/IdentifierMappingPolicy.java`
- Modify: `src/main/java/com/bank/migration/ddl/TableDdlPlanner.java`
- Modify: `src/main/java/com/bank/migration/load/DataCopyService.java`
- Modify: `src/main/java/com/bank/migration/validate/*.java`
- Test: `src/test/java/com/bank/migration/dialect/DatabaseDialectTest.java`
- Test: `src/test/java/com/bank/migration/identifier/IdentifierRendererTest.java`

## Tasks

- [ ] **Step 1: Write failing tests for reserved word rendering**

Create tests proving:

```text
Oracle source column LIMIT renders as "LIMIT" for Oracle SELECT.
Gauss target column LIMIT renders as "limit" only when policy allows rename, or "LIMIT" when quote policy is selected.
Simple identifier ACCOUNT_ID renders without quotes.
```

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=IdentifierRendererTest,DatabaseDialectTest test
```

Expected: fail because new classes do not exist.

- [ ] **Step 3: Implement dialect interfaces**

`DatabaseDialect` should expose:

```java
String renderIdentifier(String rawIdentifier);
String renderQualifiedName(String schema, String objectName);
boolean isReservedWord(String identifier);
```

`OracleDialect` should preserve Oracle uppercase source names unless quoted input is added later. `GaussDialect` should lower unquoted target names and protect reserved words.

- [ ] **Step 4: Implement reserved word registry**

Include at least the observed word `LIMIT`, plus common PostgreSQL/openGauss words: `USER`, `ORDER`, `GROUP`, `SELECT`, `WHERE`, `TABLE`, `INDEX`, `CONSTRAINT`, `PRIMARY`, `FOREIGN`, `UNIQUE`.

- [ ] **Step 5: Refactor DDL and DML rendering**

Replace raw lowercasing and direct concatenation for schema, table, column, constraint, and index names in `TableDdlPlanner` and `DataCopyService`.

- [ ] **Step 6: Add integration-style unit test**

Add a test for a table named `IBS_CUSERAPPLIMIT` with column `LIMIT`. Assert generated create-table SQL and insert SQL are valid according to the selected target policy.

- [ ] **Step 7: Run full tests**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/bank/migration/dialect src/main/java/com/bank/migration/identifier src/main/java/com/bank/migration/ddl src/main/java/com/bank/migration/load src/main/java/com/bank/migration/validate src/test/java/com/bank/migration/dialect src/test/java/com/bank/migration/identifier
git commit -m "feat: add dialect identifier rendering"
```

## Acceptance Gate

- No source SQL uses target dialect rendering.
- No target SQL inserts raw reserved identifiers.
- The observed `LIMIT` column case has a test.

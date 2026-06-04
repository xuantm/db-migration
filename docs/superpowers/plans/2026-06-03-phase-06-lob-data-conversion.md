# Phase 06 LOB Data Conversion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert Oracle LOB and temporal JDBC values into target-safe Java values before batch insert into GaussDB/PostgreSQL.

**Architecture:** Add a value conversion layer inside data loading. `DataCopyService` should delegate source value extraction to converters instead of passing raw JDBC driver objects through.

**Tech Stack:** Java 21, JDBC `Clob`, `NClob`, `Blob`, Spring JDBC, JUnit 5, Mockito.

---

## Files

- Create: `src/main/java/com/bank/migration/load/conversion/SourceValueConverter.java`
- Create: `src/main/java/com/bank/migration/load/conversion/JdbcSourceValueConverter.java`
- Create: `src/main/java/com/bank/migration/load/conversion/LobConversionException.java`
- Modify: `src/main/java/com/bank/migration/load/DataCopyService.java`
- Test: `src/test/java/com/bank/migration/load/conversion/JdbcSourceValueConverterTest.java`
- Test: `src/test/java/com/bank/migration/load/DataCopyServiceTest.java`

## Tasks

- [ ] **Step 1: Write failing converter tests**

Assert:

```text
Clob -> String
NClob -> String
Blob -> byte[]
null -> null
oracle.sql.DATE-like class name -> Timestamp via ResultSet.getTimestamp(column)
oracle.sql.TIMESTAMP-like class name -> Timestamp via ResultSet.getTimestamp(column)
oracle.sql.INTERVAL-like class name -> String
```

- [ ] **Step 2: Run focused tests and verify failure**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=JdbcSourceValueConverterTest,DataCopyServiceTest test
```

Expected: fail because converter does not exist or CLOB is not converted.

- [ ] **Step 3: Implement converter interface**

Use this method shape:

```java
Object convert(ResultSet rs, String columnName, Object rawValue) throws SQLException;
```

- [ ] **Step 4: Implement LOB conversion**

Use `Clob.getSubString(1, Math.toIntExact(clob.length()))` for current phase. If length exceeds `Integer.MAX_VALUE`, throw `LobConversionException` with table/column context once wired.

- [ ] **Step 5: Refactor DataCopyService**

Inject `SourceValueConverter` and call it for every selected column. Preserve current interval/date/timestamp behavior through the converter.

- [ ] **Step 6: Add chunk error context**

When conversion fails, chunk error logging must include phase `DATA_COPY`, table name, chunk id, column name in the message, and original exception class.

- [ ] **Step 7: Run full tests**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/bank/migration/load src/test/java/com/bank/migration/load
git commit -m "feat: convert Oracle LOB values before load"
```

## Acceptance Gate

- `java.sql.Clob`, `NClob`, and `Blob` are not passed directly to target batch insert.
- Conversion failures include table, chunk, and column context.
- Existing data copy tests still pass.

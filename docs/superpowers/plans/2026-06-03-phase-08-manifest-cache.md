# Phase 08 Manifest Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and reload scanned migration manifests with version checks so repeated review/test runs can skip Oracle metadata scanning safely.

**Architecture:** Store manifest JSON in target audit schema with cache key, schema, tool version, scanner version, config hash, and checksum. Orchestrator chooses scan or cache based on config.

**Tech Stack:** Java 21, Jackson, Spring JDBC, SHA-256, JUnit 5.

---

## Files

- Modify: `src/main/java/com/bank/migration/config/MigrationProperties.java`
- Modify: `src/main/java/com/bank/migration/audit/AuditSchemaService.java`
- Create: `src/main/java/com/bank/migration/manifest/ManifestCacheProperties.java`
- Create: `src/main/java/com/bank/migration/manifest/ManifestCacheRecord.java`
- Create: `src/main/java/com/bank/migration/manifest/ManifestCacheStore.java`
- Create: `src/main/java/com/bank/migration/manifest/ManifestCacheService.java`
- Modify: `src/main/java/com/bank/migration/orchestrator/MigrationOrchestrator.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/bank/migration/manifest/ManifestCacheServiceTest.java`
- Test: `src/test/java/com/bank/migration/audit/AuditSchemaServiceTest.java`

## Tasks

- [ ] **Step 1: Write failing property binding test**

Bind:

```yaml
migration:
  manifest-cache:
    enabled: true
    load-from-cache: false
    save-after-scan: true
    cache-key: EBANK_FULL_SCAN
    fail-if-cache-missing: false
```

- [ ] **Step 2: Write failing cache service tests**

Assert:

```text
saveAfterScan=true stores JSON manifest.
loadFromCache=true returns cached manifest when version and hash match.
failIfCacheMissing=true throws clear exception when no row exists.
version mismatch throws clear exception.
checksum mismatch throws clear exception.
```

- [ ] **Step 3: Run focused tests and verify failure**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn -Dtest=ManifestCacheServiceTest,AuditSchemaServiceTest,MigrationPropertiesTest test
```

Expected: fail before implementation.

- [ ] **Step 4: Add audit cache table**

Create `migration_audit.manifest_cache`:

```sql
cache_key varchar(200) primary key,
source_schema varchar(200) not null,
manifest_version varchar(50) not null,
tool_version varchar(50) not null,
scanner_version varchar(50) not null,
config_hash varchar(64) not null,
manifest_checksum varchar(64) not null,
manifest_json text not null,
created_at timestamp not null
```

- [ ] **Step 5: Implement serialization**

Use Jackson `ObjectMapper` already available in the app. Sort map keys and include JavaTime module for stable JSON.

- [ ] **Step 6: Wire orchestrator**

If `load-from-cache=true`, load manifest and skip `OracleMetadataScanner.scan(...)`. If scan runs and `save-after-scan=true`, save manifest before readiness evaluation.

- [ ] **Step 7: Run full tests**

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected: all tests pass.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/bank/migration/config src/main/java/com/bank/migration/audit src/main/java/com/bank/migration/manifest src/main/java/com/bank/migration/orchestrator src/main/resources/application.yml src/test/java/com/bank/migration
git commit -m "feat: add migration manifest cache"
```

## Acceptance Gate

- Cache never loads on version/hash/checksum mismatch.
- Cache missing behavior follows config.
- Oracle scan can be skipped only when cache validation passes.

# Oracle To GaussDB Migration Tool

Offline one-time clean-load migration tool for moving Oracle banking table data and compatible normal views to GaussDB.

## Phase-One Scope

Migrated:

- Tables
- Table data
- Primary keys
- Foreign keys
- Unique constraints
- Indexes
- Compatible normal views

Not migrated:

- Triggers
- Procedures
- Functions
- Packages
- Jobs and schedulers
- Materialized views
- CDC or incremental changes

## Operating Model

Phase one assumes a downtime cutoff:

1. Stop application writes to Oracle.
2. Confirm no business writes are still running.
3. Run the migration tool once.
4. Review audit checkpoints, errors, and validation reports.
5. Cut application traffic to GaussDB only after validation is accepted.

The target schema is treated as disposable when `MIGRATION_CLEAN_LOAD=true`. The tool drops and recreates the target schema, then creates tables, loads data, adds indexes/constraints, applies compatible views, validates integrity, and writes reports.

## Required Configuration

Set these environment variables before running:

- `MIGRATION_SOURCE_JDBC_URL`
- `MIGRATION_SOURCE_USERNAME`
- `MIGRATION_SOURCE_PASSWORD`
- `MIGRATION_SOURCE_DRIVER_CLASS_NAME`
- `MIGRATION_SOURCE_SCHEMA`
- `MIGRATION_TARGET_JDBC_URL`
- `MIGRATION_TARGET_USERNAME`
- `MIGRATION_TARGET_PASSWORD`
- `MIGRATION_TARGET_DRIVER_CLASS_NAME`
- `MIGRATION_TARGET_SCHEMA`

Optional:

- `MIGRATION_CLEAN_LOAD`, default `true`
- `MIGRATION_BATCH_CHUNK_SIZE`, default `5000`
- `MIGRATION_BATCH_FETCH_SIZE`, default `5000`
- `MIGRATION_BATCH_MAX_PARALLEL_TABLES`, default `2`
- `MIGRATION_REPORT_OUTPUT_DIR`, default `build/migration-reports`
- `MIGRATION_MANIFEST_CACHE_ENABLED`, default `false`
- `MIGRATION_MANIFEST_CACHE_LOAD_FROM_CACHE`, default `false`
- `MIGRATION_MANIFEST_CACHE_SAVE_AFTER_SCAN`, default `false`
- `MIGRATION_MANIFEST_CACHE_CACHE_KEY`, default `DEFAULT_KEY`
- `MIGRATION_MANIFEST_CACHE_FAIL_IF_CACHE_MISSING`, default `false`
- `MIGRATION_MODE`, default `FULL`. Set to `DATA_ONLY` for data-only mode.
- `MIGRATION_DATA_ONLY_TARGET_DATA_POLICY`, default `REQUIRE_EMPTY`
- `MIGRATION_DATA_ONLY_FOREIGN_KEY_HANDLING`, default `DISABLE_REENABLE`. Options: `DISABLE_REENABLE`, `ORDER_ONLY`, `DBA_MANAGED`.

## Data-Only Mode

Use data-only mode when GaussDB/openGauss DDL has already been created by DBA-approved scripts and the migration tool must only copy Oracle table data.

```yaml
migration:
  mode: DATA_ONLY
  clean-load: true
  data-only:
    target-data-policy: REQUIRE_EMPTY
    foreign-key-handling: DISABLE_REENABLE
```

In `DATA_ONLY` mode the tool skips:

- target schema cleanup
- table creation
- primary key, unique, index, and foreign key DDL
- view creation

The tool still runs:

- source metadata scan
- exclusions
- readiness checks for data-copy risks
- target table/column compatibility checks
- chunked data copy
- row-count validation
- duplicate-key validation
- source-manifest FK validation
- target-catalog FK validation
- audit and filesystem reports

### Foreign Key Handling in Data-Only Mode

`DISABLE_REENABLE` executes `ALTER TABLE <schema>.<table> DISABLE TRIGGER ALL` before loading included tables and `ALTER TABLE <schema>.<table> ENABLE TRIGGER ALL` after loading. This usually requires table owner, DBA, or elevated privileges. The tool always attempts to re-enable triggers in a `finally` block.

If the migration user cannot disable triggers, use:

```yaml
migration:
  data-only:
    foreign-key-handling: ORDER_ONLY
```

`ORDER_ONLY` loads parent tables before child tables based on Oracle FK metadata and fails on circular FK dependencies. Use `DBA_MANAGED` only when the DBA disables and re-enables FK triggers outside the tool. All modes run target FK orphan validation after data load.

## Run

```bash
mvn -q -DskipTests package
java -jar target/oracle-gaussdb-migration-0.1.0-SNAPSHOT.jar
```

## Pipeline

1. Run preflight checks for Oracle connectivity, GaussDB connectivity, and target schema safety.
2. Ensure `migration_audit` tables exist.
3. Scan Oracle tables, columns, constraints, indexes, and normal views.
4. Create GaussDB tables.
5. Copy data in chunks.
6. Add primary keys, unique constraints, indexes, and foreign keys.
7. Apply compatible normal views.
8. Validate row counts, duplicate keys, and foreign-key orphan counts.
9. Write JSON, CSV, and HTML reports.

## Data Chunking

Tables with a single-column primary key are migrated by primary-key ranges. Tables without a single-column primary key use Oracle `ROWID` window chunks so large tables are still bounded by `MIGRATION_BATCH_CHUNK_SIZE`.

For banking sign-off, review chunk counts and source table sizes before the production cutoff window.

## Audit And Error Logs

The tool writes operational audit records to the target database:

- `migration_audit.checkpoints`
- `migration_audit.errors`
- `migration_audit.runs`
- `migration_audit.phase_status`
- `migration_audit.manifest_cache`

Reports are written to `MIGRATION_REPORT_OUTPUT_DIR`:

- JSON for automation and full machine-readable details
- CSV for spreadsheet review
- HTML for audit review

Error records include phase, object type, object name, chunk id, database code when available, SQL text or chunk predicate, and the exception message.

## Integrity Checks

Validation includes:

- Source row count vs target row count per table
- Duplicate checks for primary and unique keys
- Foreign-key orphan checks after constraints are added
- MD5 checksum validation of all scalar columns
- Sample row data value comparisons (up to 100 sample rows per table)

The final report status is:

- `PASS` when validations pass and all views are compatible
- `WARNING` when validations pass but at least one view needs manual review
- `FAIL` when a validation fails or the migration stops with an error

## Safety Notes

- Back up Oracle and GaussDB before running.
- Run first against disposable schemas with production-like volumes.
- Keep Oracle read consistency expectations explicit during the downtime cutoff.
- Do not reuse phase one as an incremental sync tool.
- Do not run against a non-disposable target schema with `MIGRATION_CLEAN_LOAD=true`.

## E2E Integration Testing

An automated E2E integration test suite is provided to verify the tool against real Oracle and openGauss instances.

### Prerequisites

- Docker and Docker Compose installed and running.
- PowerShell (for running the test script).
- Java 21 SDK (on the host system).

### Running the E2E Test

To spin up the databases, populate the sample schema using Swingbench, and execute the migration:

```powershell
./run-e2e-test.ps1
```

The script performs the following:
1. Packages the migration tool via a Dockerized Maven container.
2. Spins up the database containers:
   - Oracle Free (`gvenzl/oracle-free:slim`) on host port `1521` (password `OraclePass123`).
   - openGauss (`enmotech/opengauss:6.0.0`) on host port `15432` (password `GaussPass123!`).
3. Waits until both databases are fully healthy.
4. Cleans the target `soe` schema on openGauss.
5. Invokes a one-time Swingbench container to generate the `SOE` sample schema on Oracle (using scale `0.05` for a quick integration run).
6. Runs the migration jar locally, pointing to the database ports.

### Clean up

To stop the containers and clean up E2E resources:

```bash
docker compose down --remove-orphans
```


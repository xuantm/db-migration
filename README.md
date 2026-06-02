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

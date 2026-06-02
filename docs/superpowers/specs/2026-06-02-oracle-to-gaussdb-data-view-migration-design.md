# Oracle To GaussDB Data And View Migration Design

## Goal

Build an offline, one-time migration tool that moves banking data from Oracle to GaussDB after a planned cutoff. The tool scans Oracle metadata, creates the required GaussDB structures, migrates table data, migrates compatible views, validates integrity, and produces detailed audit/error reports.

## Scope

In scope:

- Scan Oracle table metadata: schemas, tables, columns, primary keys, foreign keys, unique constraints, indexes, and normal views.
- Generate and apply GaussDB DDL for tables, primary keys, foreign keys, unique constraints, indexes, and normal views.
- Load Oracle table data into GaussDB using chunked, restartable batch jobs.
- Preserve data integrity through validation: row counts, checksums, duplicate key checks, foreign-key orphan checks, and metadata comparisons.
- Log errors with enough detail to diagnose, replay, and relay failures.
- Produce final migration reports in machine-readable and human-readable formats.

Out of scope for phase one:

- CDC or incremental sync.
- Trigger migration.
- Procedure, function, package, and job migration.
- Application SQL conversion.
- Materialized view migration.
- Automatic conversion of unsupported Oracle-specific view SQL.

## Recommended Stack

Use Java or Kotlin with Spring Boot and Spring Batch.

Spring Batch is the core migration engine because it provides chunk-oriented processing, restartability, transaction boundaries, retry handling, and job metadata. Spring Boot provides configuration, CLI packaging, structured logging, dependency injection, metrics, and operational wiring.

Core libraries:

- Oracle JDBC driver for source reads.
- GaussDB JDBC driver for target writes.
- Spring Batch for chunked jobs, checkpointing, and restart.
- Flyway or Liquibase only for the tool's internal metadata schema, not for converting Oracle application schema.
- Jackson for manifest/report JSON.
- Logback with JSON layout for structured logs.

## Architecture

The tool is organized into focused modules:

- `schema-scanner`: Reads Oracle metadata for tables, columns, keys, indexes, and views.
- `ddl-planner`: Converts Oracle metadata into an executable GaussDB migration plan.
- `ddl-applier`: Creates schemas, tables, constraints, indexes, and views in GaussDB.
- `migration-runner`: Coordinates Spring Batch jobs for data load.
- `chunk-planner`: Splits tables into deterministic chunks using primary keys or Oracle `ROWID` fallback.
- `checkpoint-store`: Tracks migration run, table, and chunk status.
- `error-log-store`: Persists detailed error records for failed phases, tables, chunks, and SQL operations.
- `validator`: Runs row-count, checksum, duplicate, foreign-key, and metadata validation.
- `reporter`: Produces final JSON, CSV, and HTML reports.

Each module exposes a narrow interface. The scanner does not execute DDL, the DDL planner does not move data, and the validator does not mutate business tables.

## Migration Flow

1. Start a `migration_run` with a unique run id, operator, config checksum, source schema, target schema, and timestamp.
2. Scan Oracle metadata and write a migration manifest.
3. Classify objects as `READY`, `WARNING`, or `NEEDS_REVIEW`.
4. Stop before migration if required table metadata is unsupported.
5. Create GaussDB schemas and tables.
6. Load table data in dependency-aware order. Parent tables are loaded before child tables when possible.
7. Create indexes, unique constraints, and foreign keys after data load.
8. Create compatible views after base tables exist.
9. Run validation.
10. Generate final migration report.

## Target Preparation Mode

Phase one uses `clean-load` mode only.

Before loading data, the tool verifies that the target schema is empty or explicitly marked disposable. If target tables already contain data and `clean-load` is not enabled, the tool fails before writing any business data.

This keeps phase one deterministic and avoids partial incremental semantics.

## Schema And Constraint Handling

Tables are created before data load. Heavy secondary indexes and foreign keys are created after data load to improve throughput and avoid ordering failures during bulk insert.

Primary keys can be created before or after load depending on performance profile:

- For small and medium tables, create primary keys before load to catch duplicates early.
- For very large tables, create primary keys after load and run duplicate checks before applying them.

Foreign keys are validated before creation by checking for orphan rows. If orphan rows exist, the foreign key is not created and the report includes parent table, child table, columns, sample keys, and offending row counts.

## View Migration

Normal Oracle views are scanned from metadata and included in the manifest.

The view planner builds a dependency graph so views are created after their base tables and after any referenced views.

View handling has three outcomes:

- `READY`: SQL is compatible or can be safely transformed to GaussDB syntax.
- `WARNING`: SQL can be applied, but uses risky constructs that should be reviewed.
- `NEEDS_REVIEW`: SQL contains Oracle-specific syntax or functions that the tool cannot safely convert.

The tool never silently changes complex business logic. Unsupported views remain in the report with original SQL, transformed SQL if any, parser/planner notes, and the GaussDB execution error if creation was attempted.

## Data Loading

Data is loaded table by table using Spring Batch jobs. Each table is split into chunks.

Chunk strategy:

- Use numeric or date primary-key ranges when available.
- Use composite primary-key ordering when deterministic range chunking is possible.
- Use Oracle `ROWID` as fallback for tables without a usable primary key.
- Flag tables without primary keys as higher risk in the manifest.

Each chunk is processed inside a transaction boundary:

- Read rows from Oracle using configured fetch size.
- Transform values according to type mapping.
- Write rows to GaussDB using batched insert.
- Commit only after the full chunk succeeds.
- Roll back the chunk on failure.

If the process stops, rerunning the same migration resumes from chunks that are not marked `SUCCESS`.

## Type Mapping

Default mappings:

- `NUMBER(p, s)` to `numeric(p, s)`.
- `NUMBER(p, 0)` to `smallint`, `integer`, or `bigint` when precision safely fits; otherwise `numeric(p, 0)`.
- `VARCHAR2`, `NVARCHAR2`, `CHAR`, and `NCHAR` to compatible character types, with byte-vs-character semantics recorded in the manifest.
- `DATE` and `TIMESTAMP` to compatible timestamp types.
- `CLOB` and `NCLOB` to text-compatible types.
- `BLOB` and `RAW` to binary-compatible types.

Any unsupported, ambiguous, or precision-risky mapping is marked `NEEDS_REVIEW` before load.

Column-level overrides are supported through configuration so banking-specific columns can be handled explicitly.

## Checkpointing

Checkpoint data is stored outside business tables in the tool metadata schema.

Checkpoint records include:

- migration run id
- schema name
- table name
- chunk id
- chunk range or rowid range
- rows read
- rows written
- status: `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, or `SKIPPED`
- retry count
- started timestamp
- completed timestamp
- last error id

Chunks are idempotent in `clean-load` mode because the target is prepared before load and a successful chunk is never replayed unless the operator explicitly resets that chunk.

## Error Logging

Every error record includes:

- migration run id
- phase: `SCAN`, `DDL`, `LOAD`, `CONSTRAINT`, `VIEW`, `VALIDATE`, or `REPORT`
- source schema and target schema
- object type
- table, column, constraint, index, or view name
- chunk id and chunk range when applicable
- SQL template or sanitized SQL
- source database error code when applicable
- target SQLSTATE when applicable
- retry count
- stack trace summary
- sample key values or row fingerprint when safe
- recommended action category

Recommended action categories include:

- unsupported type
- precision overflow
- invalid date/timestamp
- character encoding issue
- duplicate key
- missing parent row
- target object already exists
- permission issue
- SQL syntax incompatibility
- connection timeout

## Validation

Validation runs after data load and after constraints/indexes/views are applied where possible.

Required validation:

- Table row count comparison.
- Chunk-level row count comparison for large tables.
- Checksum by table or primary-key range.
- Duplicate primary-key and unique-key checks.
- Foreign-key orphan checks.
- Metadata comparison for table, column, primary-key, foreign-key, index, and view counts.
- View creation status check.

The migration is marked successful only when required validations pass. Warnings are allowed only for objects explicitly configured as non-blocking.

## Reporting

The final report includes:

- Overall status.
- Run id, start time, end time, duration, source schema, and target schema.
- Per-table status, row counts, checksum status, chunk count, retry count, and error count.
- Per-constraint and per-index status.
- Per-view status and review notes.
- Unsupported or skipped objects.
- Detailed error log references.

Reports are written as JSON for automation, CSV for spreadsheet review, and HTML for human audit.

## Operational Safeguards

The tool starts with preflight checks:

- Source Oracle connectivity.
- Target GaussDB connectivity.
- Required permissions.
- Target schema emptiness for `clean-load`.
- Available disk space for logs/reports.
- Config checksum and manifest checksum.

The tool supports dry run mode:

- Scan source metadata.
- Generate manifest.
- Generate DDL preview.
- Estimate table sizes and chunk counts.
- Do not write business data.

## Testing Strategy

Unit tests cover:

- Oracle-to-GaussDB type mapping.
- Dependency ordering.
- DDL generation.
- Chunk planning.
- Error classification.
- Manifest and report serialization.

Integration tests cover:

- Table creation.
- Data load.
- Primary key, foreign key, unique constraint, and index creation.
- View creation.
- Checkpoint resume after interrupted chunk.
- Failure handling for duplicate keys, orphan rows, invalid dates, and precision overflow.

Golden datasets include:

- Parent-child tables.
- Composite primary keys.
- Tables without primary keys.
- Nullable and default columns.
- Large numeric precision.
- Timestamp edge cases.
- Character and binary large objects.
- Compatible and incompatible views.

## Acceptance Criteria

The phase-one tool is acceptable when it can:

- Scan Oracle metadata and generate a complete manifest for supported table and view objects.
- Create GaussDB target tables, constraints, indexes, and compatible views.
- Load all supported table data in `clean-load` mode.
- Resume from failed chunks without duplicating committed chunks.
- Produce detailed, searchable error logs.
- Validate row counts, checksums, duplicates, foreign-key integrity, and object creation status.
- Fail closed when an object or type mapping is unsafe.
- Produce final JSON, CSV, and HTML reports.

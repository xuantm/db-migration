# Expected Edge Schema Readiness Findings

Running the readiness evaluation phase against the `edge_test` schema is expected to yield the following issues:

1. **Blocker (BFILE Column)**
   - **Object:** `EDGE_TEST_LOB_TABLE`
   - **Column:** `EXTERNAL_FILE`
   - **Type:** `BFILE`
   - **Finding:** Oracle `BFILE` columns are not natively supported and cannot be mapped. This represents a hard migration blocker that halts the pipeline before any target schema mutation occurs.

2. **Warning (Reserved Word Column)**
   - **Object:** `EDGE_TEST_LIMIT_TABLE`
   - **Column:** `LIMIT`
   - **Finding:** The column name matches a GaussDB reserved keyword (`LIMIT`). It requires quoting to be safely migrated.

3. **Needs Review / Warning (FK to Excluded Table)**
   - **Object:** `EDGE_TEST_CHILD_TABLE`
   - **Finding:** The foreign key constraint references the table `EDGE_TEST_EXCLUDED_PARENT` which is excluded from the migration manifest, resulting in a potential broken relation.

4. **Needs Review (Complex View)**
   - **Object:** `EDGE_TEST_COMPLEX_VIEW`
   - **Finding:** The view uses the Oracle-proprietary `ROWNUM` pseudo-column, which needs manual review or rewrite for GaussDB syntax.

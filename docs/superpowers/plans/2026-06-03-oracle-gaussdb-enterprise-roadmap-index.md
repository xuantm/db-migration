# Oracle GaussDB Enterprise Roadmap Index

This folder splits the architecture review prompt into standalone phase assignments. Give one file to one agent at a time. Each phase should produce working, reviewable output before the next dependent phase starts.

## Assignment Order

1. `2026-06-03-phase-00-baseline-architecture-review.md`
2. `2026-06-03-phase-01-dialect-identifier-framework.md`
3. `2026-06-03-phase-02-readiness-validation-risk-report.md`
4. `2026-06-03-phase-03-manifest-v2-exclusions.md`
5. `2026-06-03-phase-04-oracle-type-mapping-strategy.md`
6. `2026-06-03-phase-05-chunk-strategy-refactor.md`
7. `2026-06-03-phase-06-lob-data-conversion.md`
8. `2026-06-03-phase-07-schema-object-coverage.md`
9. `2026-06-03-phase-08-manifest-cache.md`
10. `2026-06-03-phase-09-validation-audit-observability.md`
11. `2026-06-03-phase-10-e2e-performance-testing.md`

## Shared Constraints

- Do not migrate triggers, procedures, functions, packages, jobs, or materialized views in phase-one runtime behavior.
- Scan and report out-of-scope objects when useful for readiness and audit.
- Preserve banking-grade correctness over convenience. Any uncertain mapping should become `NEEDS_REVIEW` or `BLOCKED`, not silent conversion.
- Keep implementation scoped to the assigned phase. Do not bundle unrelated refactors.
- Do not stage generated `build/` or `target/` artifacts.

## Common Verification Commands

Run focused tests after each task, then run the full suite before handoff:

```powershell
$env:DOCKER_CONFIG='G:\workspace\db-migration\.docker-tmp'
docker run --rm -v 'G:\workspace\db-migration:/workspace' -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test
```

Expected final result:

```text
Tests run: 49 or more, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

When a phase adds new tests, the total test count should increase.

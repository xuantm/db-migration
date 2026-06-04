# E2E and Performance Testing Runbook

This directory contains repeatable Docker-based end-to-end (E2E) integration and performance smoke tests for the Oracle to GaussDB migration tool.

## Prerequisites

- **Docker** and **Docker Compose**
- **PowerShell 7+** (for Windows)
- **Java 21 JDK** installed on the host

## Architecture

The E2E setup coordinates:
1. **Oracle Free Database** (`gvenzl/oracle-free:slim`) as the source.
2. **openGauss Database** (`enmotech/opengauss:6.0.0`) as the migration target.
3. **Swingbench** (`domgiles/swingbench:latest`) for load generation and sample schema setup (`SOE`).
4. **Edge Schema SQL Scripts** to initialize complex data types (LOBs, BFILEs, composite PKs, reserved words).

---

## How to Run E2E Tests

To run the complete automated E2E workflow:

```powershell
./run-e2e-test.ps1
```

This script will:
1. Compile the project and build the executable jar (`target/oracle-gaussdb-migration-0.1.0-SNAPSHOT.jar`).
2. Start the database containers.
3. Wait until both databases are fully healthy.
4. Clean the target openGauss schema `soe`.
5. Run Swingbench `oewizard` to generate standard test tables on Oracle.
6. Apply the edge schema SQL scripts for comprehensive edge-case verification.
7. Execute the migration tool against the generated datasets.

---

## Known Environment Constraints & Image Limitations

### 1. Third-Party Docker Image Defects
- **opengauss/opengauss**: The official image might crash with `libopenblas.so.0` load failures on certain CPU microarchitectures or non-Linux host operating systems.
  - **Resolution:** The setup uses `enmotech/opengauss:6.0.0` which runs stably and features better compatibility with Windows Docker Desktop.

### 2. Environment Port Conflicts
- **Port 1521 / 15432 / 5432**: If your host machine already runs an active Oracle database or standard PostgreSQL service on ports `1521` or `5432`, the docker compose services will fail to bind to the host network.
  - **Resolution:** Ensure local database instances are stopped before executing the E2E script. The openGauss container maps host port `15432` to the container's `5432` port to minimize PG port clashes on the host.

### 3. Tool Readiness Blockers
- **BFILE Support:** The presence of `BFILE` columns triggers a migration blocker finding and intentionally aborts target schema mutation. This is a design feature ensuring data integrity. To proceed to a happy path execution, exclude the blocking table (`edge_test_lob_table`) in `application.yml` config properties.

### 4. Docker Daemon Status
- If the Docker daemon is not running on the host machine, commands like `docker compose up` or `./run-e2e-test.ps1` will fail with socket connection errors (e.g. `npipe:////./pipe/docker_engine`). Ensure Docker Desktop or the Docker daemon is started before running E2E tests.

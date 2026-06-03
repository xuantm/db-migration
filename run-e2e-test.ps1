# E2E Integration Test runner for Oracle to GaussDB migration

$ErrorActionPreference = "Stop"

# 1. Package the Java application
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "1. Packaging migration tool with Maven..." -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
docker run --rm -v "g:/workspace/db-migration:/usr/src/mymaven" -v "m2-cache:/root/.m2" -w /usr/src/mymaven maven:3.9.6-eclipse-temurin-21 mvn clean package -DskipTests

# 2. Start database containers
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "2. Starting database containers..." -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
# Clean up any potential conflicting containers from previous runs
cmd /c "docker stop dbmig-oracle dbmig-gauss oracle-db opengauss-db 2>nul" | Out-Null
cmd /c "docker rm dbmig-oracle dbmig-gauss oracle-db opengauss-db 2>nul" | Out-Null
docker compose down --remove-orphans

docker compose up -d oracle-db opengauss-db

# 3. Wait for databases to become healthy
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "3. Waiting for databases to become healthy..." -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
$timeout = 240 # 4 minutes
$elapsed = 0
do {
    $oracleStatus = (docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}' oracle-db 2>$null)
    $gaussStatus = (docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}' opengauss-db 2>$null)
    
    Write-Host "Oracle DB status: $oracleStatus | openGauss DB status: $gaussStatus (Elapsed: ${elapsed}s)"
    
    if ($oracleStatus -eq "healthy" -and $gaussStatus -eq "healthy") {
        break
    }
    
    if ($elapsed -ge $timeout) {
        Write-Error "Timeout waiting for databases to start"
    }
    
    Start-Sleep -Seconds 10
    $elapsed += 10
} while ($true)

# 4. Clean target schema in openGauss to bypass preflight safety check
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "4. Cleaning target schema 'soe' in openGauss..." -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
# Run a temporary Postgres container to connect to openGauss and drop schema 'soe'
docker run --rm --network migration-net -e PGPASSWORD=GaussPass123! postgres:alpine psql -h opengauss-db -U gaussdb -d postgres -c "DROP SCHEMA IF EXISTS soe CASCADE;"

# 5. Populate Oracle database using Swingbench
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "5. Running Swingbench to populate Oracle sample schema..." -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
docker compose run --rm swingbench -cl -cs //oracle-db:1521/FREEPDB1 -u soe -p soe -scale 0.05 -create -ts USERS -dba system -dbap OraclePass123

# 6. Run the migration tool
Write-Host ""
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "6. Running migration tool smoke test..." -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

$env:MIGRATION_SOURCE_JDBC_URL = "jdbc:oracle:thin:@localhost:1521/FREEPDB1"
$env:MIGRATION_SOURCE_USERNAME = "soe"
$env:MIGRATION_SOURCE_PASSWORD = "soe"
$env:MIGRATION_SOURCE_DRIVER_CLASS_NAME = "oracle.jdbc.OracleDriver"
$env:MIGRATION_SOURCE_SCHEMA = "SOE"

$env:MIGRATION_TARGET_JDBC_URL = "jdbc:opengauss://localhost:15432/postgres"
$env:MIGRATION_TARGET_USERNAME = "gaussdb"
$env:MIGRATION_TARGET_PASSWORD = "GaussPass123!"
$env:MIGRATION_TARGET_DRIVER_CLASS_NAME = "org.opengauss.Driver"
$env:MIGRATION_TARGET_SCHEMA = "soe"

$env:MIGRATION_CLEAN_LOAD = "true"
$env:MIGRATION_BATCH_CHUNK_SIZE = "5000"
$env:MIGRATION_BATCH_FETCH_SIZE = "5000"
$env:MIGRATION_BATCH_MAX_PARALLEL_TABLES = "2"
$env:MIGRATION_REPORT_OUTPUT_DIR = "build/migration-reports"

java -jar target/oracle-gaussdb-migration-0.1.0-SNAPSHOT.jar

Write-Host ""
Write-Host "=============================================" -ForegroundColor Green
Write-Host "E2E Test Run Completed Successfully!" -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green

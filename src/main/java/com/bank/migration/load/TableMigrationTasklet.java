package com.bank.migration.load;

import com.bank.migration.audit.CheckpointRecord;
import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.audit.ChunkStatus;
import com.bank.migration.audit.ErrorLogStore;
import com.bank.migration.audit.ErrorRecord;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.TableMetadata;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;

public class TableMigrationTasklet {
    private final String runId;
    private final String targetSchema;
    private final TableMetadata table;
    private final List<ChunkPlan> chunks;
    private final DataCopyService copyService;
    private final CheckpointStore checkpointStore;
    private final ErrorLogStore errorLogStore;

    public TableMigrationTasklet(
        String runId,
        String targetSchema,
        TableMetadata table,
        List<ChunkPlan> chunks,
        DataCopyService copyService,
        CheckpointStore checkpointStore
    ) {
        this(runId, targetSchema, table, chunks, copyService, checkpointStore, null);
    }

    public TableMigrationTasklet(
        String runId,
        String targetSchema,
        TableMetadata table,
        List<ChunkPlan> chunks,
        DataCopyService copyService,
        CheckpointStore checkpointStore,
        ErrorLogStore errorLogStore
    ) {
        this.runId = runId;
        this.targetSchema = targetSchema;
        this.table = table;
        this.chunks = List.copyOf(chunks);
        this.copyService = copyService;
        this.checkpointStore = checkpointStore;
        this.errorLogStore = errorLogStore;
    }

    public void run() {
        for (ChunkPlan chunk : chunks) {
            Instant started = Instant.now();
            try {
                TableCopyResult result = copyService.copyChunk(table, targetSchema, chunk);
                checkpointStore.save(new CheckpointRecord(
                    runId,
                    table.schema(),
                    table.name(),
                    chunk.chunkId(),
                    chunk.whereClause(),
                    result.rowsRead(),
                    result.rowsWritten(),
                    ChunkStatus.SUCCESS,
                    0,
                    started,
                    Instant.now(),
                    null
                ));
            } catch (RuntimeException ex) {
                ErrorRecord error = errorRecord(chunk, ex);
                saveErrorSafely(error);
                checkpointStore.save(new CheckpointRecord(
                    runId,
                    table.schema(),
                    table.name(),
                    chunk.chunkId(),
                    chunk.whereClause(),
                    0,
                    0,
                    ChunkStatus.FAILED,
                    1,
                    started,
                    Instant.now(),
                    error.errorId()
                ));
                throw new ChunkMigrationException(error, ex);
            }
        }
    }

    private ErrorRecord errorRecord(ChunkPlan chunk, RuntimeException ex) {
        return new ErrorRecord(
            "err-" + UUID.randomUUID(),
            runId,
            "data-load",
            "TABLE",
            table.name(),
            chunk.chunkId(),
            chunk.whereClause(),
            databaseCode(ex),
            ex.getClass().getName() + ": " + ex.getMessage(),
            "RETRY_OR_MANUAL_REVIEW",
            Instant.now()
        );
    }

    private void saveErrorSafely(ErrorRecord error) {
        if (errorLogStore == null) {
            return;
        }
        try {
            errorLogStore.save(error);
        } catch (DataAccessException ignored) {
            // The failed checkpoint still stores last_error_id for cross-reference in the filesystem report.
        }
    }

    private static String databaseCode(RuntimeException ex) {
        if (ex instanceof DataAccessException dataAccessException && dataAccessException.getMostSpecificCause() != null) {
            return dataAccessException.getMostSpecificCause().getClass().getSimpleName();
        }
        return null;
    }
}

package com.bank.migration.load;

import com.bank.migration.audit.CheckpointRecord;
import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.audit.ChunkStatus;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.TableMetadata;
import java.time.Instant;
import java.util.List;

public class TableMigrationTasklet {
    private final String runId;
    private final String targetSchema;
    private final TableMetadata table;
    private final List<ChunkPlan> chunks;
    private final DataCopyService copyService;
    private final CheckpointStore checkpointStore;

    public TableMigrationTasklet(
        String runId,
        String targetSchema,
        TableMetadata table,
        List<ChunkPlan> chunks,
        DataCopyService copyService,
        CheckpointStore checkpointStore
    ) {
        this.runId = runId;
        this.targetSchema = targetSchema;
        this.table = table;
        this.chunks = List.copyOf(chunks);
        this.copyService = copyService;
        this.checkpointStore = checkpointStore;
    }

    public void run() {
        for (ChunkPlan chunk : chunks) {
            Instant started = Instant.now();
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
        }
    }
}

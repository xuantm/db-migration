package com.bank.migration.load;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.audit.CheckpointStore;
import com.bank.migration.audit.ChunkStatus;
import com.bank.migration.audit.ErrorLogStore;
import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class TableMigrationTaskletTest {
    @Mock DataCopyService copyService;
    @Mock CheckpointStore checkpointStore;
    @Mock ErrorLogStore errorLogStore;

    @Test
    void savesSuccessfulCheckpointForEachChunk() {
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        ChunkPlan chunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 10", "PRIMARY_KEY_RANGE");
        when(copyService.copyChunk(table, "bank_core", chunk)).thenReturn(new TableCopyResult(10, 10));
        TableMigrationTasklet tasklet = new TableMigrationTasklet("run-001", "bank_core", table, List.of(chunk), copyService, checkpointStore);

        tasklet.run();

        verify(checkpointStore).save(org.mockito.ArgumentMatchers.argThat(record ->
            record.runId().equals("run-001")
                && record.tableName().equals("ACCOUNT")
                && record.chunkId().equals("ACCOUNT-000001")
                && record.status() == ChunkStatus.SUCCESS
                && record.rowsRead() == 10
                && record.rowsWritten() == 10
        ));
    }

    @Test
    void savesFailedCheckpointAndErrorForFailedChunk() {
        TableMetadata table = new TableMetadata("BANK_CORE", "ACCOUNT", ObjectStatus.READY, List.of(), List.of(), List.of());
        ChunkPlan chunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 10", "PRIMARY_KEY_RANGE");
        when(copyService.copyChunk(table, "bank_core", chunk))
            .thenThrow(new DataAccessResourceFailureException("source read failed"));
        TableMigrationTasklet tasklet = new TableMigrationTasklet(
            "run-001",
            "bank_core",
            table,
            List.of(chunk),
            copyService,
            checkpointStore,
            errorLogStore
        );

        assertThatThrownBy(tasklet::run)
            .isInstanceOf(ChunkMigrationException.class)
            .hasCauseInstanceOf(DataAccessResourceFailureException.class)
            .hasMessageContaining("source read failed");

        verify(errorLogStore).save(org.mockito.ArgumentMatchers.argThat(error ->
            error.runId().equals("run-001")
                && error.phase().equals("data-load")
                && error.objectName().equals("ACCOUNT")
                && error.chunkId().equals("ACCOUNT-000001")
                && error.message().contains("source read failed")
        ));
        verify(checkpointStore).save(org.mockito.ArgumentMatchers.argThat(record ->
            record.status() == ChunkStatus.FAILED
                && record.tableName().equals("ACCOUNT")
                && record.chunkId().equals("ACCOUNT-000001")
                && record.lastErrorId() != null
                && record.retryCount() == 1
        ));
    }
}

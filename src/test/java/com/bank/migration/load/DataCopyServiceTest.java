package com.bank.migration.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class DataCopyServiceTest {
    @Mock JdbcTemplate sourceJdbc;
    @Mock JdbcTemplate targetJdbc;

    @Test
    @SuppressWarnings("unchecked")
    void copiesChunkRowsWithBatchInsert() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 19, 0, false, null),
                new ColumnMetadata("ACCOUNT_NO", "VARCHAR2", 32, null, false, null)
            ),
            List.of(),
            List.of()
        );
        ChunkPlan chunk = new ChunkPlan("ACCOUNT-000001", "ID", "ID >= 1 and ID <= 2", "PRIMARY_KEY_RANGE");
        when(sourceJdbc.query(
            eq("select ID, ACCOUNT_NO from BANK_CORE.ACCOUNT where ID >= 1 and ID <= 2"),
            any(RowMapper.class)
        )).thenReturn(List.of(
            Map.of("ID", 1L, "ACCOUNT_NO", "A001"),
            Map.of("ID", 2L, "ACCOUNT_NO", "A002")
        ));
        when(targetJdbc.batchUpdate(eq("insert into bank_core.account (id, account_no) values (?, ?)"), anyList()))
            .thenReturn(new int[] {1, 1});

        DataCopyService service = new DataCopyService(sourceJdbc, targetJdbc);

        TableCopyResult result = service.copyChunk(table, "bank_core", chunk);

        assertThat(result.rowsRead()).isEqualTo(2);
        assertThat(result.rowsWritten()).isEqualTo(2);
        verify(targetJdbc).batchUpdate(
            eq("insert into bank_core.account (id, account_no) values (?, ?)"),
            anyList()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsZeroWhenChunkHasNoRows() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "EMPTY_TABLE",
            ObjectStatus.READY,
            List.of(new ColumnMetadata("ID", "NUMBER", 18, 0, false, null)),
            List.of(),
            List.of()
        );
        ChunkPlan chunk = new ChunkPlan("EMPTY_TABLE-000001", "ID", "ID >= 1 and ID <= 2", "PRIMARY_KEY_RANGE");
        when(sourceJdbc.query(any(String.class), any(RowMapper.class))).thenReturn(List.of());

        DataCopyService service = new DataCopyService(sourceJdbc, targetJdbc);

        TableCopyResult result = service.copyChunk(table, "bank_core", chunk);

        assertThat(result).isEqualTo(new TableCopyResult(0, 0));
    }
}

package com.bank.migration.load;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bank.migration.chunk.ChunkPlan;
import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.dialect.OracleDialect;
import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.load.conversion.JdbcSourceValueConverter;
import com.bank.migration.load.conversion.LobConversionException;
import com.bank.migration.load.conversion.SourceValueConverter;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private final IdentifierRenderer sourceRenderer = new IdentifierRenderer(new OracleDialect());
    private final IdentifierRenderer targetRendererQuote = new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.QUOTE));
    private final IdentifierRenderer targetRendererRename = new IdentifierRenderer(new GaussDialect(IdentifierMappingPolicy.RENAME));
    private final JdbcSourceValueConverter converter = new JdbcSourceValueConverter();

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

        DataCopyService service = new DataCopyService(sourceJdbc, targetJdbc, sourceRenderer, targetRendererQuote, converter);

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

        DataCopyService service = new DataCopyService(sourceJdbc, targetJdbc, sourceRenderer, targetRendererQuote, converter);

        TableCopyResult result = service.copyChunk(table, "bank_core", chunk);

        assertThat(result).isEqualTo(new TableCopyResult(0, 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handlesReservedWordColumnLimit() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "IBS_CUSERAPPLIMIT",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 19, 0, false, null),
                new ColumnMetadata("LIMIT", "NUMBER", 19, 0, false, null)
            ),
            List.of(),
            List.of()
        );
        ChunkPlan chunk = new ChunkPlan("IBS_CUSERAPPLIMIT-000001", "ID", "ID >= 1 and ID <= 2", "PRIMARY_KEY_RANGE");

        // 1. Test under QUOTE policy
        when(sourceJdbc.query(
            eq("select ID, LIMIT from BANK_CORE.IBS_CUSERAPPLIMIT where ID >= 1 and ID <= 2"),
            any(RowMapper.class)
        )).thenReturn(List.of(
            Map.of("ID", 1L, "LIMIT", 1000L)
        ));

        when(targetJdbc.batchUpdate(eq("insert into bank_core.ibs_cuserapplimit (id, \"LIMIT\") values (?, ?)"), anyList()))
            .thenReturn(new int[] {1});

        DataCopyService serviceQuote = new DataCopyService(sourceJdbc, targetJdbc, sourceRenderer, targetRendererQuote, converter);
        TableCopyResult resultQuote = serviceQuote.copyChunk(table, "bank_core", chunk);
        assertThat(resultQuote.rowsRead()).isEqualTo(1);

        // 2. Test under RENAME policy
        when(targetJdbc.batchUpdate(eq("insert into bank_core.ibs_cuserapplimit (id, limit_) values (?, ?)"), anyList()))
            .thenReturn(new int[] {1});

        DataCopyService serviceRename = new DataCopyService(sourceJdbc, targetJdbc, sourceRenderer, targetRendererRename, converter);
        TableCopyResult resultRename = serviceRename.copyChunk(table, "bank_core", chunk);
        assertThat(resultRename.rowsRead()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void copiesLobsCorrectlyExecutingRowMapper() throws Exception {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "LOB_TABLE",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 19, 0, false, null),
                new ColumnMetadata("MY_CLOB", "CLOB", null, null, false, null),
                new ColumnMetadata("MY_NCLOB", "NCLOB", null, null, false, null),
                new ColumnMetadata("MY_BLOB", "BLOB", null, null, false, null)
            ),
            List.of(),
            List.of()
        );
        ChunkPlan chunk = new ChunkPlan("LOB_TABLE-000001", "ID", "ID >= 1 and ID <= 2", "PRIMARY_KEY_RANGE");

        Clob mockClob = mock(Clob.class);
        NClob mockNClob = mock(NClob.class);
        Blob mockBlob = mock(Blob.class);

        when(mockClob.length()).thenReturn(5L);
        when(mockClob.getSubString(1, 5)).thenReturn("clob_val");

        when(mockNClob.length()).thenReturn(6L);
        when(mockNClob.getSubString(1, 6)).thenReturn("nclob_val");

        byte[] blobBytes = new byte[]{1, 2, 3};
        when(mockBlob.length()).thenReturn(3L);
        when(mockBlob.getBytes(1, 3)).thenReturn(blobBytes);

        when(sourceJdbc.query(
            eq("select ID, MY_CLOB, MY_NCLOB, MY_BLOB from BANK_CORE.LOB_TABLE where ID >= 1 and ID <= 2"),
            any(RowMapper.class)
        )).thenAnswer(invocation -> {
            RowMapper<Map<String, Object>> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getObject("ID")).thenReturn(1L);
            when(rs.getObject("MY_CLOB")).thenReturn(mockClob);
            when(rs.getObject("MY_NCLOB")).thenReturn(mockNClob);
            when(rs.getObject("MY_BLOB")).thenReturn(mockBlob);

            Map<String, Object> row = mapper.mapRow(rs, 0);
            return List.of(row);
        });

        when(targetJdbc.batchUpdate(eq("insert into bank_core.lob_table (id, my_clob, my_nclob, my_blob) values (?, ?, ?, ?)"), anyList()))
            .thenReturn(new int[] {1});

        DataCopyService service = new DataCopyService(sourceJdbc, targetJdbc, sourceRenderer, targetRendererQuote, converter);

        TableCopyResult result = service.copyChunk(table, "bank_core", chunk);

        assertThat(result.rowsRead()).isEqualTo(1);
        assertThat(result.rowsWritten()).isEqualTo(1);

        org.mockito.ArgumentCaptor<List<Object[]>> argsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(targetJdbc).batchUpdate(any(String.class), argsCaptor.capture());

        List<Object[]> batchArgs = argsCaptor.getValue();
        assertThat(batchArgs).hasSize(1);
        Object[] rowArgs = batchArgs.get(0);
        assertThat(rowArgs[0]).isEqualTo(1L);
        assertThat(rowArgs[1]).isEqualTo("clob_val");
        assertThat(rowArgs[2]).isEqualTo("nclob_val");
        assertThat((byte[]) rowArgs[3]).containsExactly((byte) 1, (byte) 2, (byte) 3);

        assertThat(rowArgs[1]).isInstanceOf(String.class);
        assertThat(rowArgs[2]).isInstanceOf(String.class);
        assertThat(rowArgs[3]).isInstanceOf(byte[].class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsLobConversionExceptionWithContextOnRowMapperSqlException() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "LOB_TABLE",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 19, 0, false, null),
                new ColumnMetadata("MY_CLOB", "CLOB", null, null, false, null)
            ),
            List.of(),
            List.of()
        );
        ChunkPlan chunk = new ChunkPlan("LOB-000001", "ID", "ID >= 1 and ID <= 2", "PRIMARY_KEY_RANGE");

        when(sourceJdbc.query(any(String.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper<Map<String, Object>> mapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("ID")).thenReturn(1L);
                when(rs.getObject("MY_CLOB")).thenThrow(new SQLException("Connection lost"));

                mapper.mapRow(rs, 0);
                return List.of();
            });

        DataCopyService service = new DataCopyService(sourceJdbc, targetJdbc, sourceRenderer, targetRendererQuote, converter);

        assertThatThrownBy(() -> service.copyChunk(table, "bank_core", chunk))
            .isInstanceOf(LobConversionException.class)
            .hasMessageContaining("Phase: DATA_COPY")
            .hasMessageContaining("Table: LOB_TABLE")
            .hasMessageContaining("Chunk: LOB-000001")
            .hasMessageContaining("Column: MY_CLOB")
            .hasMessageContaining("Cause: java.sql.SQLException");
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsLobConversionExceptionWithContextWhenConverterThrowsLobConversionExceptionWithCause() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "LOB_TABLE",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 19, 0, false, null),
                new ColumnMetadata("MY_CLOB", "CLOB", null, null, false, null)
            ),
            List.of(),
            List.of()
        );
        ChunkPlan chunk = new ChunkPlan("LOB-000001", "ID", "ID >= 1 and ID <= 2", "PRIMARY_KEY_RANGE");

        SourceValueConverter mockConverter = mock(SourceValueConverter.class);

        when(sourceJdbc.query(any(String.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper<Map<String, Object>> mapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("ID")).thenReturn(1L);
                when(rs.getObject("MY_CLOB")).thenReturn("raw_value");

                mapper.mapRow(rs, 0);
                return List.of();
            });

        try {
            lenient().when(mockConverter.convert(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
            lenient().when(mockConverter.convert(any(), eq("MY_CLOB"), any()))
                .thenThrow(new LobConversionException("Length too long", new SQLException("Connection reset")));
        } catch (SQLException ignored) {}

        DataCopyService service = new DataCopyService(sourceJdbc, targetJdbc, sourceRenderer, targetRendererQuote, mockConverter);

        assertThatThrownBy(() -> service.copyChunk(table, "bank_core", chunk))
            .isInstanceOf(LobConversionException.class)
            .hasMessageContaining("Phase: DATA_COPY")
            .hasMessageContaining("Table: LOB_TABLE")
            .hasMessageContaining("Chunk: LOB-000001")
            .hasMessageContaining("Column: MY_CLOB")
            .hasMessageContaining("Cause: java.sql.SQLException");
    }

    @Test
    @SuppressWarnings("unchecked")
    void throwsLobConversionExceptionWithContextWhenConverterThrowsLobConversionExceptionWithoutCause() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "LOB_TABLE",
            ObjectStatus.READY,
            List.of(
                new ColumnMetadata("ID", "NUMBER", 19, 0, false, null),
                new ColumnMetadata("MY_CLOB", "CLOB", null, null, false, null)
            ),
            List.of(),
            List.of()
        );
        ChunkPlan chunk = new ChunkPlan("LOB-000001", "ID", "ID >= 1 and ID <= 2", "PRIMARY_KEY_RANGE");

        SourceValueConverter mockConverter = mock(SourceValueConverter.class);

        when(sourceJdbc.query(any(String.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper<Map<String, Object>> mapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("ID")).thenReturn(1L);
                when(rs.getObject("MY_CLOB")).thenReturn("raw_value");

                mapper.mapRow(rs, 0);
                return List.of();
            });

        try {
            lenient().when(mockConverter.convert(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
            lenient().when(mockConverter.convert(any(), eq("MY_CLOB"), any()))
                .thenThrow(new LobConversionException("Custom conversion error", null));
        } catch (SQLException ignored) {}

        DataCopyService service = new DataCopyService(sourceJdbc, targetJdbc, sourceRenderer, targetRendererQuote, mockConverter);

        assertThatThrownBy(() -> service.copyChunk(table, "bank_core", chunk))
            .isInstanceOf(LobConversionException.class)
            .hasMessageContaining("Phase: DATA_COPY")
            .hasMessageContaining("Table: LOB_TABLE")
            .hasMessageContaining("Chunk: LOB-000001")
            .hasMessageContaining("Column: MY_CLOB")
            .hasMessageContaining("Cause: com.bank.migration.load.conversion.LobConversionException");
    }
}

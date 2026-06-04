package com.bank.migration.chunk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bank.migration.domain.ColumnMetadata;
import com.bank.migration.domain.KeyMetadata;
import com.bank.migration.domain.ObjectStatus;
import com.bank.migration.domain.TableMetadata;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ChunkBoundsServiceTest {
    @Mock JdbcTemplate sourceJdbc;

    @Test
    void readsMinAndMaxForSingleColumnPrimaryKey() {
        TableMetadata table = new TableMetadata(
            "BANK_CORE",
            "ACCOUNT",
            ObjectStatus.READY,
            List.of(new ColumnMetadata("ID", "NUMBER", 18, 0, false, null)),
            List.of(new KeyMetadata("PK_ACCOUNT", "PRIMARY_KEY", List.of("ID"), null, null)),
            List.of()
        );
        when(sourceJdbc.queryForObject("select min(ID) from BANK_CORE.ACCOUNT", Long.class)).thenReturn(10L);
        when(sourceJdbc.queryForObject("select max(ID) from BANK_CORE.ACCOUNT", Long.class)).thenReturn(20L);

        ChunkBounds bounds = new ChunkBoundsService(sourceJdbc).bounds(table);

        assertThat(bounds).isEqualTo(new ChunkBounds(10L, 20L));
    }

    @Test
    void readsRowCountWhenNoSingleColumnPrimaryKeyExists() {
        TableMetadata table = new TableMetadata("BANK_CORE", "AUDIT_LOG", ObjectStatus.READY, List.of(), List.of(), List.of());
        when(sourceJdbc.queryForObject("select count(*) from BANK_CORE.AUDIT_LOG", Long.class)).thenReturn(12_000L);

        ChunkBounds bounds = new ChunkBoundsService(sourceJdbc).bounds(table);

        assertThat(bounds).isEqualTo(new ChunkBounds(1L, 12_000L));
    }
}

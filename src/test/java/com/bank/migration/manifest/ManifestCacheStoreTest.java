package com.bank.migration.manifest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class ManifestCacheStoreTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void saveUsesAtomicUpsertOnConflictDoUpdate() {
        ManifestCacheStore store = new ManifestCacheStore(jdbc);
        ManifestCacheRecord record = new ManifestCacheRecord(
            "MY_KEY", "SRC_SCH", "2.0", "0.1.0", "1.0", "hash", "checksum", "{}", LocalDateTime.now()
        );
        store.save(record);

        verify(jdbc).update(
            org.mockito.ArgumentMatchers.contains("on conflict (cache_key) do update set"),
            eq(record.cacheKey()),
            eq(record.sourceSchema()),
            eq(record.manifestVersion()),
            eq(record.toolVersion()),
            eq(record.scannerVersion()),
            eq(record.configHash()),
            eq(record.manifestChecksum()),
            eq(record.manifestJson()),
            any(java.sql.Timestamp.class)
        );
    }
}

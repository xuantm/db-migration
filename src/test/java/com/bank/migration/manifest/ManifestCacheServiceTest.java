package com.bank.migration.manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.scanner.OracleMetadataScanner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManifestCacheServiceTest {
    private static final String TOOL_VERSION = "0.1.0-SNAPSHOT";

    private ManifestCacheStore store;
    private ManifestCacheService service;
    private MigrationProperties properties;
    private MigrationManifest manifest;

    @BeforeEach
    void setUp() {
        store = mock(ManifestCacheStore.class);
        service = new ManifestCacheService(
            store,
            new ObjectMapper().registerModule(new JavaTimeModule()),
            TOOL_VERSION,
            OracleMetadataScanner.SCANNER_CONTRACT_VERSION
        );

        MigrationProperties.Database dbSource = new MigrationProperties.Database(
            "jdbc:oracle:thin:@//host:1521/DB", "user", "pass", "oracle.jdbc.OracleDriver", "SRC_SCH"
        );
        MigrationProperties.Database dbTarget = new MigrationProperties.Database(
            "jdbc:postgresql://host:5432/db", "user", "pass", "org.postgresql.Driver", "target_sch"
        );
        MigrationProperties.Batch batch = new MigrationProperties.Batch(1000, 1000, 2);
        MigrationProperties.Reports reports = new MigrationProperties.Reports("build/reports");
        MigrationProperties.Excluded excluded = new MigrationProperties.Excluded(List.of("EX_TAB"), List.of(), List.of());
        ManifestCacheProperties cacheProps = new ManifestCacheProperties(true, true, true, "MY_KEY", false);

        properties = new MigrationProperties(
            dbSource, dbTarget, false, batch, reports, null, excluded, cacheProps
        );

        manifest = new MigrationManifest("run-123", "SRC_SCH", List.of(), List.of());
    }

    @Test
    void saveStoresJsonManifestWhenEnabled() {
        service.save(properties, manifest);

        ArgumentCaptor<ManifestCacheRecord> captor = ArgumentCaptor.forClass(ManifestCacheRecord.class);
        verify(store).save(captor.capture());

        ManifestCacheRecord record = captor.getValue();
        assertThat(record.cacheKey()).isEqualTo("MY_KEY");
        assertThat(record.sourceSchema()).isEqualTo("SRC_SCH");
        assertThat(record.manifestVersion()).isEqualTo(ManifestCacheService.MANIFEST_VERSION);
        assertThat(record.toolVersion()).isEqualTo(TOOL_VERSION);
        assertThat(record.scannerVersion()).isEqualTo(OracleMetadataScanner.SCANNER_CONTRACT_VERSION);
        assertThat(record.manifestJson()).contains("run-123");
        assertThat(record.configHash()).isNotNull();
        assertThat(record.manifestChecksum()).isNotNull();
    }

    @Test
    void loadReturnsCachedManifestWhenMatched() {
        // Save first to capture valid checksum and config hash
        service.save(properties, manifest);
        ArgumentCaptor<ManifestCacheRecord> captor = ArgumentCaptor.forClass(ManifestCacheRecord.class);
        verify(store).save(captor.capture());
        ManifestCacheRecord savedRecord = captor.getValue();

        when(store.findByKey("MY_KEY")).thenReturn(Optional.of(savedRecord));

        Optional<MigrationManifest> loaded = service.load(properties);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().runId()).isEqualTo("run-123");
    }

    @Test
    void loadThrowsWhenCacheMissingAndFailIfCacheMissingIsTrue() {
        ManifestCacheProperties cacheProps = new ManifestCacheProperties(true, true, true, "MY_KEY", true);
        MigrationProperties propsWithFail = new MigrationProperties(
            properties.source(), properties.target(), properties.cleanLoad(),
            properties.batch(), properties.reports(), properties.identifierPolicy(),
            properties.excluded(), cacheProps
        );

        when(store.findByKey("MY_KEY")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.load(propsWithFail))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Manifest cache entry missing for key: MY_KEY");
    }

    @Test
    void loadReturnsEmptyWhenCacheMissingAndFailIfCacheMissingIsFalse() {
        when(store.findByKey("MY_KEY")).thenReturn(Optional.empty());

        Optional<MigrationManifest> loaded = service.load(properties);
        assertThat(loaded).isEmpty();
    }

    @Test
    void loadThrowsOnVersionMismatch() {
        ManifestCacheRecord badRecord = new ManifestCacheRecord(
            "MY_KEY", "SRC_SCH", "1.0-OLD", TOOL_VERSION, OracleMetadataScanner.SCANNER_CONTRACT_VERSION,
            "hash", "checksum", "{}", LocalDateTime.now()
        );
        when(store.findByKey("MY_KEY")).thenReturn(Optional.of(badRecord));

        assertThatThrownBy(() -> service.load(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Manifest version mismatch");
    }

    @Test
    void loadThrowsOnChecksumMismatch() {
        ManifestCacheRecord badRecord = new ManifestCacheRecord(
            "MY_KEY", "SRC_SCH", ManifestCacheService.MANIFEST_VERSION, TOOL_VERSION, OracleMetadataScanner.SCANNER_CONTRACT_VERSION,
            "hash", "wrong_checksum", "{}", LocalDateTime.now()
        );
        when(store.findByKey("MY_KEY")).thenReturn(Optional.of(badRecord));

        assertThatThrownBy(() -> service.load(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("checksum mismatch");
    }

    @Test
    void loadThrowsOnToolVersionMismatch() {
        ManifestCacheRecord badRecord = new ManifestCacheRecord(
            "MY_KEY", "SRC_SCH", ManifestCacheService.MANIFEST_VERSION, "0.0.0-old", OracleMetadataScanner.SCANNER_CONTRACT_VERSION,
            "hash", "checksum", "{}", LocalDateTime.now()
        );
        when(store.findByKey("MY_KEY")).thenReturn(Optional.of(badRecord));

        assertThatThrownBy(() -> service.load(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Tool version mismatch");
    }

    @Test
    void loadThrowsOnScannerVersionMismatch() {
        ManifestCacheRecord badRecord = new ManifestCacheRecord(
            "MY_KEY", "SRC_SCH", ManifestCacheService.MANIFEST_VERSION, TOOL_VERSION, "oracle-metadata-scan-v0",
            "hash", "checksum", "{}", LocalDateTime.now()
        );
        when(store.findByKey("MY_KEY")).thenReturn(Optional.of(badRecord));

        assertThatThrownBy(() -> service.load(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Scanner version mismatch");
    }

    @Test
    void loadReturnsEmptyOnConfigHashMismatchAndFailIfMissingIsFalse() {
        // Save with one source schema
        service.save(properties, manifest);
        ArgumentCaptor<ManifestCacheRecord> captor = ArgumentCaptor.forClass(ManifestCacheRecord.class);
        verify(store).save(captor.capture());
        ManifestCacheRecord savedRecord = captor.getValue();

        when(store.findByKey("MY_KEY")).thenReturn(Optional.of(savedRecord));

        // Create new properties with different source schema
        MigrationProperties.Database newDbSource = new MigrationProperties.Database(
            "jdbc:oracle:thin:@//host:1521/DB", "user", "pass", "oracle.jdbc.OracleDriver", "DIFFERENT_SCH"
        );
        MigrationProperties newProps = new MigrationProperties(
            newDbSource, properties.target(), properties.cleanLoad(),
            properties.batch(), properties.reports(), properties.identifierPolicy(),
            properties.excluded(), properties.manifestCache()
        );

        Optional<MigrationManifest> loaded = service.load(newProps);
        assertThat(loaded).isEmpty();
    }

    @Test
    void loadReturnsCachedManifestEvenWhenExclusionsOrIdentifierPolicyChange() {
        // Save with one set of properties
        service.save(properties, manifest);
        ArgumentCaptor<ManifestCacheRecord> captor = ArgumentCaptor.forClass(ManifestCacheRecord.class);
        verify(store).save(captor.capture());
        ManifestCacheRecord savedRecord = captor.getValue();

        when(store.findByKey("MY_KEY")).thenReturn(Optional.of(savedRecord));

        // Create new properties with different exclusions and identifier policy
        MigrationProperties.Excluded newExcluded = new MigrationProperties.Excluded(List.of("DIFFERENT_TAB"), List.of(), List.of());
        MigrationProperties newProps = new MigrationProperties(
            properties.source(), properties.target(), properties.cleanLoad(),
            properties.batch(), properties.reports(), com.bank.migration.identifier.IdentifierMappingPolicy.QUOTE,
            newExcluded, properties.manifestCache()
        );

        Optional<MigrationManifest> loaded = service.load(newProps);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().runId()).isEqualTo("run-123");
    }

    @Test
    void publicConstructorResolvesNonBlankToolVersionFallback() {
        ManifestCacheService defaultService = new ManifestCacheService(
            store,
            new ObjectMapper().registerModule(new JavaTimeModule())
        );

        defaultService.save(properties, manifest);

        ArgumentCaptor<ManifestCacheRecord> captor = ArgumentCaptor.forClass(ManifestCacheRecord.class);
        verify(store, atLeastOnce()).save(captor.capture());
        ManifestCacheRecord record = captor.getAllValues().getLast();
        assertThat(record.toolVersion()).isNotBlank();
        assertThat(record.scannerVersion()).isEqualTo(OracleMetadataScanner.SCANNER_CONTRACT_VERSION);
    }

    @Test
    void getEffectiveCacheKeyUsesDefaultKeyWithSchemaWhenBlankOrDefault() {
        // 1. When cacheKey is "DEFAULT_KEY"
        ManifestCacheProperties defaultCacheProps = new ManifestCacheProperties(true, true, true, "DEFAULT_KEY", false);
        MigrationProperties props1 = new MigrationProperties(
            properties.source(), properties.target(), properties.cleanLoad(),
            properties.batch(), properties.reports(), properties.identifierPolicy(),
            properties.excluded(), defaultCacheProps
        );
        service.save(props1, manifest);
        ArgumentCaptor<ManifestCacheRecord> captor1 = ArgumentCaptor.forClass(ManifestCacheRecord.class);
        verify(store, times(1)).save(captor1.capture());
        assertThat(captor1.getValue().cacheKey()).isEqualTo("DEFAULT_KEY_SRC_SCH");

        // 2. When cacheKey is null/blank
        ManifestCacheProperties blankCacheProps = new ManifestCacheProperties(true, true, true, "   ", false);
        MigrationProperties props2 = new MigrationProperties(
            properties.source(), properties.target(), properties.cleanLoad(),
            properties.batch(), properties.reports(), properties.identifierPolicy(),
            properties.excluded(), blankCacheProps
        );
        service.save(props2, manifest);
        ArgumentCaptor<ManifestCacheRecord> captor2 = ArgumentCaptor.forClass(ManifestCacheRecord.class);
        verify(store, times(2)).save(captor2.capture());
        assertThat(captor2.getValue().cacheKey()).isEqualTo("DEFAULT_KEY_SRC_SCH");
    }
}

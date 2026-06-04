package com.bank.migration.manifest;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.domain.MigrationManifest;
import com.bank.migration.scanner.OracleMetadataScanner;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ManifestCacheService {
    public static final String MANIFEST_VERSION = "2.0";

    private final ManifestCacheStore store;
    private final ObjectMapper mapper;
    private final String toolVersion;
    private final String scannerVersion;

    public ManifestCacheService(ManifestCacheStore store, ObjectMapper objectMapper) {
        this(store, objectMapper, resolveToolVersion(), OracleMetadataScanner.SCANNER_CONTRACT_VERSION);
    }

    ManifestCacheService(ManifestCacheStore store, ObjectMapper objectMapper, String toolVersion, String scannerVersion) {
        this.store = store;
        this.mapper = objectMapper.copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        this.toolVersion = toolVersion;
        this.scannerVersion = scannerVersion;
    }

    public void save(MigrationProperties props, MigrationManifest manifest) {
        if (props.manifestCache() == null || !props.manifestCache().enabled() || !props.manifestCache().saveAfterScan()) {
            return;
        }

        try {
            String manifestJson = mapper.writeValueAsString(manifest);
            String manifestChecksum = sha256(manifestJson);
            String configHash = calculateConfigHash(props);

            ManifestCacheRecord record = new ManifestCacheRecord(
                getEffectiveCacheKey(props),
                props.source().schema(),
                MANIFEST_VERSION,
                toolVersion,
                scannerVersion,
                configHash,
                manifestChecksum,
                manifestJson,
                LocalDateTime.now()
            );

            store.save(record);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save manifest to cache", e);
        }
    }

    public Optional<MigrationManifest> load(MigrationProperties props) {
        if (props.manifestCache() == null || !props.manifestCache().enabled() || !props.manifestCache().loadFromCache()) {
            return Optional.empty();
        }

        String cacheKey = getEffectiveCacheKey(props);
        Optional<ManifestCacheRecord> recordOpt = store.findByKey(cacheKey);

        if (recordOpt.isEmpty()) {
            if (props.manifestCache().failIfCacheMissing()) {
                throw new IllegalStateException("Manifest cache entry missing for key: " + cacheKey);
            }
            return Optional.empty();
        }

        ManifestCacheRecord record = recordOpt.get();

        // 1. Version checks: must always match
        if (!MANIFEST_VERSION.equals(record.manifestVersion())) {
            throw new IllegalStateException(String.format(
                "Manifest version mismatch. Expected: %s, Found: %s", MANIFEST_VERSION, record.manifestVersion()
            ));
        }
        if (!toolVersion.equals(record.toolVersion())) {
            throw new IllegalStateException(String.format(
                "Tool version mismatch. Expected: %s, Found: %s", toolVersion, record.toolVersion()
            ));
        }
        if (!scannerVersion.equals(record.scannerVersion())) {
            throw new IllegalStateException(String.format(
                "Scanner version mismatch. Expected: %s, Found: %s", scannerVersion, record.scannerVersion()
            ));
        }

        // 2. Checksum validation: must always match
        String calculatedChecksum = sha256(record.manifestJson());
        if (!calculatedChecksum.equals(record.manifestChecksum())) {
            throw new IllegalStateException("Manifest cache record checksum mismatch (corrupted data).");
        }

        // 3. Config hash validation: fail or fallback
        String currentConfigHash = calculateConfigHash(props);
        if (!currentConfigHash.equals(record.configHash())) {
            if (props.manifestCache().failIfCacheMissing()) {
                throw new IllegalStateException("Manifest cache configuration hash mismatch.");
            }
            return Optional.empty();
        }

        try {
            MigrationManifest manifest = mapper.readValue(record.manifestJson(), MigrationManifest.class);
            return Optional.of(manifest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize cached manifest", e);
        }
    }

    private String getEffectiveCacheKey(MigrationProperties props) {
        String cacheKey = props.manifestCache() != null ? props.manifestCache().cacheKey() : null;
        if (cacheKey == null || cacheKey.isBlank() || "DEFAULT_KEY".equalsIgnoreCase(cacheKey.trim())) {
            String schema = props.source() != null ? props.source().schema() : "UNKNOWN";
            return "DEFAULT_KEY_" + schema;
        }
        return cacheKey;
    }

    private String calculateConfigHash(MigrationProperties props) {
        try {
            // Hash the parts of the configuration that affect the schema scan
            ScannedConfig config = new ScannedConfig(
                props.source().schema(),
                props.source().jdbcUrl()
            );
            return sha256(mapper.writeValueAsString(config));
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate config hash", e);
        }
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate SHA-256", e);
        }
    }

    private static String resolveToolVersion() {
        Package toolPackage = ManifestCacheService.class.getPackage();
        if (toolPackage != null && toolPackage.getImplementationVersion() != null && !toolPackage.getImplementationVersion().isBlank()) {
            return toolPackage.getImplementationVersion();
        }
        Package appPackage = com.bank.migration.MigrationToolApplication.class.getPackage();
        if (appPackage != null && appPackage.getImplementationVersion() != null && !appPackage.getImplementationVersion().isBlank()) {
            return appPackage.getImplementationVersion();
        }
        return "dev-unpackaged";
    }

    private record ScannedConfig(
        String sourceSchema,
        String jdbcUrl
    ) {}
}

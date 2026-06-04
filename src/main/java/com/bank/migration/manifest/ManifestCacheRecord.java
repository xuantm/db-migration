package com.bank.migration.manifest;

import java.time.LocalDateTime;

public record ManifestCacheRecord(
    String cacheKey,
    String sourceSchema,
    String manifestVersion,
    String toolVersion,
    String scannerVersion,
    String configHash,
    String manifestChecksum,
    String manifestJson,
    LocalDateTime createdAt
) {}

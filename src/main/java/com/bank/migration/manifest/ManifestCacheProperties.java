package com.bank.migration.manifest;

public record ManifestCacheProperties(
    boolean enabled,
    boolean loadFromCache,
    boolean saveAfterScan,
    String cacheKey,
    boolean failIfCacheMissing
) {
    public ManifestCacheProperties {
        if (cacheKey == null || cacheKey.isBlank()) {
            cacheKey = "DEFAULT_KEY";
        }
    }
}

package com.bank.migration.manifest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ManifestCacheStore {
    private final JdbcTemplate jdbc;

    public ManifestCacheStore(@Qualifier("targetJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(ManifestCacheRecord record) {
        String sql = """
            insert into migration_audit.manifest_cache
            (cache_key, source_schema, manifest_version, tool_version, scanner_version, config_hash, manifest_checksum, manifest_json, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (cache_key) do update set
              source_schema = excluded.source_schema,
              manifest_version = excluded.manifest_version,
              tool_version = excluded.tool_version,
              scanner_version = excluded.scanner_version,
              config_hash = excluded.config_hash,
              manifest_checksum = excluded.manifest_checksum,
              manifest_json = excluded.manifest_json,
              created_at = excluded.created_at
            """;
        
        jdbc.update(
            sql,
            record.cacheKey(),
            record.sourceSchema(),
            record.manifestVersion(),
            record.toolVersion(),
            record.scannerVersion(),
            record.configHash(),
            record.manifestChecksum(),
            record.manifestJson(),
            Timestamp.valueOf(record.createdAt())
        );
    }

    public Optional<ManifestCacheRecord> findByKey(String cacheKey) {
        String sql = """
            select cache_key, source_schema, manifest_version, tool_version, scanner_version, config_hash, manifest_checksum, manifest_json, created_at
            from migration_audit.manifest_cache
            where cache_key = ?
            """;
        return jdbc.query(sql, this::mapRow, cacheKey).stream().findFirst();
    }

    private ManifestCacheRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ManifestCacheRecord(
            rs.getString("cache_key"),
            rs.getString("source_schema"),
            rs.getString("manifest_version"),
            rs.getString("tool_version"),
            rs.getString("scanner_version"),
            rs.getString("config_hash"),
            rs.getString("manifest_checksum"),
            rs.getString("manifest_json"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}

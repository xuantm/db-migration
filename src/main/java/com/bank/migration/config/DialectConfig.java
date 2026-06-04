package com.bank.migration.config;

import com.bank.migration.dialect.DatabaseDialect;
import com.bank.migration.dialect.OracleDialect;
import com.bank.migration.dialect.GaussDialect;
import com.bank.migration.identifier.IdentifierRenderer;
import com.bank.migration.identifier.IdentifierMappingPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DialectConfig {

    @Bean
    @Qualifier("sourceDialect")
    public DatabaseDialect sourceDialect() {
        return new OracleDialect();
    }

    @Bean
    @Qualifier("targetDialect")
    public DatabaseDialect targetDialect(MigrationProperties properties) {
        IdentifierMappingPolicy policy = properties.identifierPolicy() != null 
            ? properties.identifierPolicy() 
            : IdentifierMappingPolicy.QUOTE;
        return new GaussDialect(policy);
    }

    @Bean
    @Qualifier("sourceIdentifierRenderer")
    public IdentifierRenderer sourceIdentifierRenderer(@Qualifier("sourceDialect") DatabaseDialect sourceDialect) {
        return new IdentifierRenderer(sourceDialect);
    }

    @Bean
    @Qualifier("targetIdentifierRenderer")
    public IdentifierRenderer targetIdentifierRenderer(@Qualifier("targetDialect") DatabaseDialect targetDialect) {
        return new IdentifierRenderer(targetDialect);
    }
}

package com.bank.migration.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DataSourceConfig {
    @Bean
    @Qualifier("sourceDataSource")
    DataSource sourceDataSource(MigrationProperties properties) {
        return dataSource(properties.source());
    }

    @Bean
    @Qualifier("targetDataSource")
    DataSource targetDataSource(MigrationProperties properties) {
        return dataSource(properties.target());
    }

    @Bean
    @Qualifier("sourceJdbc")
    JdbcTemplate sourceJdbc(@Qualifier("sourceDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    @Qualifier("targetJdbc")
    JdbcTemplate targetJdbc(@Qualifier("targetDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private HikariDataSource dataSource(MigrationProperties.Database database) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(database.jdbcUrl());
        dataSource.setUsername(database.username());
        dataSource.setPassword(database.password());
        dataSource.setDriverClassName(database.driverClassName());
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }
}

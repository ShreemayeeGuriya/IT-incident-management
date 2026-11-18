package com.incident.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.SmartInitializingSingleton;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Locale;

@TestConfiguration(proxyBeanMethods = false)
class PostgresTestDatabaseGuard {

    @Bean
    SmartInitializingSingleton requireRadarPostgresTestDatabase(DataSource dataSource) {
        return () -> {
            try (var connection = dataSource.getConnection()) {
                String driverName = connection.getMetaData().getDriverName();
                if (driverName == null || !driverName.toLowerCase(Locale.ROOT).contains("postgresql")) {
                    throw new IllegalStateException(
                            "PostgreSQL integration tests require the PostgreSQL driver and database IT_incident_management");
                }

                String databaseName;
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT current_database()")) {
                    if (!result.next()) {
                        throw new IllegalStateException(
                                "PostgreSQL integration tests could not determine the connected database");
                    }
                    databaseName = result.getString(1);
                }

                if (!"IT_incident_management".equals(databaseName)) {
                    throw new IllegalStateException(
                            "PostgreSQL integration tests require the PostgreSQL driver and database IT_incident_management");
                }
            } catch (SQLException exception) {
                throw new IllegalStateException(
                        "PostgreSQL integration tests could not verify their configured datasource", exception);
            }
        };
    }
}

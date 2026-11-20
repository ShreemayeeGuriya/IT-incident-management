package com.incident.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/** Applies idempotent reference-data and category-skill seeds to PostgreSQL at startup. */
@Component
public class ReferenceDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataInitializer.class);

    private final DataSource dataSource;
    private final Resource referenceDataSeed;
    private final Resource skillSeed;

    public ReferenceDataInitializer(DataSource dataSource,
                                    @Value("classpath:db/001_reference_data.sql") Resource referenceDataSeed,
                                    @Value("classpath:db/002_skills.sql") Resource skillSeed) {
        this.dataSource = dataSource;
        this.referenceDataSeed = referenceDataSeed;
        this.skillSeed = skillSeed;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String database = connection.getMetaData().getDatabaseProductName();
            if (!"PostgreSQL".equalsIgnoreCase(database)) {
                log.info("Skipping PostgreSQL reference-data seed for {}", database);
                return;
            }
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(referenceDataSeed, skillSeed);
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, dataSource);
        log.info("Applied idempotent category, service, SLA, and technician skill reference data");
    }
}

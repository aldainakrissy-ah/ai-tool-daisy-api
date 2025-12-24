package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Service for monitoring database health and providing fallback mechanisms
 */
@Service
public class DatabaseHealthService {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseHealthService.class);

    @Autowired
    private DataSource primaryDataSource;

    @Autowired(required = false)
    @Qualifier("secondDataSource")
    private DataSource secondDataSource;

    /**
     * Check if the primary database is healthy
     * 
     * @return true if connection is healthy, false otherwise
     */
    public boolean isPrimaryDatabaseHealthy() {
        return checkDatabaseHealth(primaryDataSource, "Primary");
    }

    /**
     * Check if the second database is healthy
     * 
     * @return true if connection is healthy, false otherwise
     */
    @ConditionalOnProperty(name = "second.database.enabled", havingValue = "true", matchIfMissing = false)
    public boolean isSecondDatabaseHealthy() {
        if (secondDataSource == null) {
            logger.warn("Second database is not configured or disabled");
            return false;
        }
        return checkDatabaseHealth(secondDataSource, "Second");
    }

    /**
     * Get database health status for both databases
     * 
     * @return DatabaseHealthStatus object containing health information
     */
    public DatabaseHealthStatus getDatabaseHealthStatus() {
        boolean primaryHealthy = isPrimaryDatabaseHealthy();
        boolean secondHealthy = secondDataSource != null ? isSecondDatabaseHealthy() : false;

        return DatabaseHealthStatus.builder()
                .primaryDatabaseHealthy(primaryHealthy)
                .secondDatabaseHealthy(secondHealthy)
                .secondDatabaseEnabled(secondDataSource != null)
                .overallHealthy(primaryHealthy) // Application can run with just primary DB
                .build();
    }

    /**
     * Generic method to check database health
     * 
     * @param dataSource   the datasource to check
     * @param databaseName name for logging purposes
     * @return true if healthy, false otherwise
     */
    private boolean checkDatabaseHealth(DataSource dataSource, String databaseName) {
        if (dataSource == null) {
            logger.warn("{} database datasource is null", databaseName);
            return false;
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean isValid = connection.isValid(5); // 5 second timeout
            if (isValid) {
                logger.debug("{} database connection is healthy", databaseName);
            } else {
                logger.warn("{} database connection validation failed", databaseName);
            }
            return isValid;
        } catch (SQLException e) {
            logger.warn("{} database health check failed: {}", databaseName, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("{} database health check encountered unexpected error: {}", databaseName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Data class for database health status
     */
    public static class DatabaseHealthStatus {
        private final boolean primaryDatabaseHealthy;
        private final boolean secondDatabaseHealthy;
        private final boolean secondDatabaseEnabled;
        private final boolean overallHealthy;

        private DatabaseHealthStatus(boolean primaryDatabaseHealthy, boolean secondDatabaseHealthy,
                boolean secondDatabaseEnabled, boolean overallHealthy) {
            this.primaryDatabaseHealthy = primaryDatabaseHealthy;
            this.secondDatabaseHealthy = secondDatabaseHealthy;
            this.secondDatabaseEnabled = secondDatabaseEnabled;
            this.overallHealthy = overallHealthy;
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean isPrimaryDatabaseHealthy() {
            return primaryDatabaseHealthy;
        }

        public boolean isSecondDatabaseHealthy() {
            return secondDatabaseHealthy;
        }

        public boolean isSecondDatabaseEnabled() {
            return secondDatabaseEnabled;
        }

        public boolean isOverallHealthy() {
            return overallHealthy;
        }

        public static class Builder {
            private boolean primaryDatabaseHealthy;
            private boolean secondDatabaseHealthy;
            private boolean secondDatabaseEnabled;
            private boolean overallHealthy;

            public Builder primaryDatabaseHealthy(boolean primaryDatabaseHealthy) {
                this.primaryDatabaseHealthy = primaryDatabaseHealthy;
                return this;
            }

            public Builder secondDatabaseHealthy(boolean secondDatabaseHealthy) {
                this.secondDatabaseHealthy = secondDatabaseHealthy;
                return this;
            }

            public Builder secondDatabaseEnabled(boolean secondDatabaseEnabled) {
                this.secondDatabaseEnabled = secondDatabaseEnabled;
                return this;
            }

            public Builder overallHealthy(boolean overallHealthy) {
                this.overallHealthy = overallHealthy;
                return this;
            }

            public DatabaseHealthStatus build() {
                return new DatabaseHealthStatus(primaryDatabaseHealthy, secondDatabaseHealthy,
                        secondDatabaseEnabled, overallHealthy);
            }
        }

        @Override
        public String toString() {
            return "DatabaseHealthStatus{" +
                    "primaryDatabaseHealthy=" + primaryDatabaseHealthy +
                    ", secondDatabaseHealthy=" + secondDatabaseHealthy +
                    ", secondDatabaseEnabled=" + secondDatabaseEnabled +
                    ", overallHealthy=" + overallHealthy +
                    '}';
        }
    }
}

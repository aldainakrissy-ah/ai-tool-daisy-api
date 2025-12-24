package com.example.ai.tool.analysis.phase_three_api.configuration;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(name = "second.database.enabled", havingValue = "true", matchIfMissing = false)
@EnableJpaRepositories(entityManagerFactoryRef = "secondEntityManagerFactory", transactionManagerRef = "secondTransactionManager", basePackages = {
        "com.example.ai.tool.analysis.phase_three_api.repository" })
public class SecondDBConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecondDBConfig.class);

    @Bean(name = "secondDataSource")
    @ConfigurationProperties(prefix = "spring.second-datasource")
    public DataSource secondDataSource() {
        logger.info("Creating second database data source with direct SSL connection...");

        try {
            HikariDataSource dataSource = DataSourceBuilder.create()
                    .type(HikariDataSource.class)
                    .build();

            // Configure connection pool for external database
            dataSource.setConnectionTimeout(30000);
            dataSource.setIdleTimeout(600000);
            dataSource.setMaxLifetime(1800000);
            dataSource.setMinimumIdle(1);
            dataSource.setMaximumPoolSize(5);

            // Test the connection
            try (var connection = dataSource.getConnection()) {
                logger.info("Successfully established connection to second database");
            } catch (Exception testException) {
                logger.warn("Failed to test connection to second database, but datasource created: {}",
                        testException.getMessage());
            }

            return dataSource;
        } catch (Exception e) {
            logger.error("Failed to create second database data source: {}", e.getMessage(), e);
            // Return a dummy datasource to prevent application startup failure
            logger.warn("Creating fallback datasource to allow application startup");
            return DataSourceBuilder.create()
                    .type(HikariDataSource.class)
                    .driverClassName("org.h2.Driver")
                    .url("jdbc:h2:mem:fallback;DB_CLOSE_DELAY=-1")
                    .username("sa")
                    .password("")
                    .build();
        }
    }

    @Bean(name = "secondEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean secondEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("secondDataSource") DataSource dataSource) {

        try {
            return builder
                    .dataSource(dataSource)
                    .packages("com.example.ai.tool.analysis.phase_three_api.entity")
                    .persistenceUnit("phase_three_api")
                    .properties(hibernateProperties())
                    .build();
        } catch (Exception e) {
            logger.error("Failed to create second database entity manager factory: {}", e.getMessage(), e);
            logger.warn("Creating fallback entity manager factory to allow application startup");

            // Create fallback properties with less strict validation
            Map<String, Object> fallbackProperties = new HashMap<>();
            fallbackProperties.put("hibernate.hbm2ddl.auto", "none");
            fallbackProperties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
            fallbackProperties.put("hibernate.show_sql", false);
            fallbackProperties.put("hibernate.format_sql", false);
            fallbackProperties.put("hibernate.connection.provider_disables_autocommit", false);
            fallbackProperties.put("hibernate.temp.use_jdbc_metadata_defaults", false);

            return builder
                    .dataSource(dataSource)
                    .packages("com.example.ai.tool.analysis.phase_three_api.entity")
                    .persistenceUnit("phase_three_api_fallback")
                    .properties(fallbackProperties)
                    .build();
        }
    }

    @Bean(name = "secondTransactionManager")
    public PlatformTransactionManager secondTransactionManager(
            @Qualifier("secondEntityManagerFactory") EntityManagerFactory secondEntityManagerFactory) {
        if (secondEntityManagerFactory == null) {
            logger.warn("EntityManagerFactory is null, creating a dummy transaction manager");
            return new JpaTransactionManager(); // Create without entity manager factory
        }
        return new JpaTransactionManager(secondEntityManagerFactory);
    }

    /**
     * Health check method for the second database connection
     * 
     * @param dataSource the second database datasource
     * @return true if connection is healthy, false otherwise
     */
    public boolean isSecondDatabaseHealthy(@Qualifier("secondDataSource") DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(5); // 5 second timeout
        } catch (Exception e) {
            logger.warn("Second database health check failed: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> hibernateProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.show_sql", false);
        properties.put("hibernate.format_sql", false);

        // Additional properties for external database connections
        properties.put("hibernate.connection.provider_disables_autocommit", false);
        properties.put("hibernate.temp.use_jdbc_metadata_defaults", false);

        // Add properties to handle connection failures gracefully
        properties.put("hibernate.connection.acquisition_timeout", "30000");
        properties.put("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_RELEASE_AFTER_STATEMENT");

        return properties;
    }
}
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
@EnableJpaRepositories(
    entityManagerFactoryRef = "secondEntityManagerFactory", 
    transactionManagerRef = "secondTransactionManager", 
    basePackages = {"com.example.ai.tool.analysis.phase_three_api.repository"}
)
public class SecondDBConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecondDBConfig.class);

    @Bean(name = "secondDataSource")
    @ConfigurationProperties(prefix = "spring.second-datasource")
    public DataSource secondDataSource() {
        logger.info("Creating second database data source with direct SSL connection...");
        
        HikariDataSource dataSource = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
        
        // Configure connection pool for external database
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
        dataSource.setMinimumIdle(1);
        dataSource.setMaximumPoolSize(5);
        
        return dataSource;
    }

    @Bean(name = "secondEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean secondEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("secondDataSource") DataSource dataSource) {

        return builder
                .dataSource(dataSource)
                .packages("com.example.ai.tool.analysis.phase_three_api.entity")
                .persistenceUnit("phase_three_api")
                .properties(hibernateProperties())
                .build();
    }

    @Bean(name = "secondTransactionManager")
    public PlatformTransactionManager secondTransactionManager(
            @Qualifier("secondEntityManagerFactory") EntityManagerFactory secondEntityManagerFactory) {
        return new JpaTransactionManager(secondEntityManagerFactory);
    }

    private Map<String, Object> hibernateProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "validate");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.show_sql", false);
        properties.put("hibernate.format_sql", false);
        
        // Additional properties for external database connections
        properties.put("hibernate.connection.provider_disables_autocommit", false);
        properties.put("hibernate.temp.use_jdbc_metadata_defaults", false);
        
        return properties;
    }
}
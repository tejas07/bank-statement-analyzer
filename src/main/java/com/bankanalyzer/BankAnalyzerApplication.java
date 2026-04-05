package com.bankanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JPA, DataSource, and Flyway auto-configurations are excluded here.
 * They are conditionally re-enabled by PersistenceConfig when persistence.enabled=true.
 * This ensures no database connection is attempted when persistence is disabled (default).
 */
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,
    FlywayAutoConfiguration.class
})
@EnableCaching
@EnableScheduling
public class BankAnalyzerApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankAnalyzerApplication.class, args);
    }
}

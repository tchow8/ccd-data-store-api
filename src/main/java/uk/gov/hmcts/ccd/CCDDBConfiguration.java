package uk.gov.hmcts.ccd;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    entityManagerFactoryRef = "ccdEntityManagerFactory",
    transactionManagerRef = "ccdTransactionManager",
    basePackages = { "uk.gov.hmcts.ccd.data" }
)
@Slf4j
public class CCDDBConfiguration {

    @Primary
    @Bean
    @Qualifier("ccdDataSourceProperties")
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties ccdDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean
    @Qualifier("ccdDataSource")
//    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource ccdDataSource(@Qualifier("ccdDataSourceProperties") DataSourceProperties datasourceProperties) {
        return datasourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Primary
    @Bean(name = "ccdEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean ccdEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("ccdDataSource") DataSource dataSource
    ) {
        return builder
            .dataSource(dataSource)
            .packages("uk.gov.hmcts.ccd.data")
            .persistenceUnit("ccd")
            .build();
    }
//
//    @Primary
//    @Bean(name = "ccdSharedEntityManagerCreator")
//    public SharedEntityManagerCreator ccdSharedEntityManagerCreator(
//        EntityManagerFactoryBuilder builder,
//        @Qualifier("ccdDataSource") DataSource dataSource
//    ) {
//        return builder
//            .dataSource(dataSource)
//            .packages("uk.gov.hmcts.ccd.data")
//            .persistenceUnit("ccd")
//            .build();
//    }

    @Primary
    @Bean(name = "ccdTransactionManager")
    public PlatformTransactionManager ccdTransactionManager(
        @Qualifier("ccdEntityManagerFactory") EntityManagerFactory
            entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }

}

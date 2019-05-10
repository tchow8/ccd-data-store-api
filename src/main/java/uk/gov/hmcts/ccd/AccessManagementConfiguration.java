package uk.gov.hmcts.ccd;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import uk.gov.hmcts.reform.amlib.AccessManagementService;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    entityManagerFactoryRef = "amsEntityManagerFactory",
    transactionManagerRef = "amsTransactionManager",
    basePackages = { "uk.gov.hmcts.reform.domain.service.accesscontrol", "uk.gov.hmcts.reform.amlib" }
)
@Slf4j
public class AccessManagementConfiguration {

    @Bean
    @Qualifier("accessManagementDataSourceProperties")
    @ConfigurationProperties("accessmanagement.datasource")
    public DataSourceProperties accessManagementDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Qualifier("accessManagementDataSource")
//    @ConfigurationProperties("accessmanagement.datasource.hikari")
    public DataSource accessManagementDataSource(@Qualifier("accessManagementDataSourceProperties") DataSourceProperties datasourceProperties) {
        return datasourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = "amsEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean amsEntityManagerFactory(
        EntityManagerFactoryBuilder builder,
        @Qualifier("accessManagementDataSource") DataSource dataSource
    ) {
        return builder
            .dataSource(dataSource)
            .packages("uk.gov.hmcts.reform.domain.service.accesscontrol", "uk.gov.hmcts.reform.amlib")
            .persistenceUnit("ams")
            .build();
    }

    @Bean(name = "amsTransactionManager")
    public PlatformTransactionManager transactionManager(
        @Qualifier("amsEntityManagerFactory") EntityManagerFactory
            entityManagerFactory
    ) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    @Autowired
    public AccessManagementService defaultAccessManagementService(@Qualifier("accessManagementDataSource") DataSource datasource) {
        return new AccessManagementService(datasource);
    }
}

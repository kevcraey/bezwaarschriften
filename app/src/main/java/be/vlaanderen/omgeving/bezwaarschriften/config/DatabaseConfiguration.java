package be.vlaanderen.omgeving.bezwaarschriften.config;

import be.milieinfo.framework.db.DatabaseGranter;
import be.milieinfo.framework.db.DatabaseOwnershipTransfer;
import be.milieinfo.framework.db.PostgresDatabaseForeignkeyIndexCreator;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class DatabaseConfiguration {
  @Configuration
  @Profile(Constants.SPRING_PROFILE_PRODUCTION)
  public static class ProdDatabaseConfiguration {
    @Value("${spring.liquibase.url}")
    private String url;
    @Value("${spring.liquibase.user}")
    private String username;
    @Value("${spring.liquibase.password}")
    private String password;

    @Bean(name = "schemaDataSource")
    public DataSource schemaDataSource() throws SQLException {
      return DataSourceBuilder.create().url(url).username(username)
        .password(password).type(DriverManagerDataSource.class).build();
    }

    @Bean
    public DatabaseOwnershipTransfer ownershipTranser(
            @Qualifier(value = "schemaDataSource") DataSource dataSource,
            @Value("${spring.liquibase.user}") String from, 
            @Value("${spring.datasource.ddl-user-role}") String to) {
      return new DatabaseOwnershipTransfer(from, to, dataSource);
    }

    @Bean
    public PostgresDatabaseForeignkeyIndexCreator postgresDatabaseForeignKeyIndexCreator(
        @Qualifier(value = "schemaDataSource") DataSource dataSource) {
      return new PostgresDatabaseForeignkeyIndexCreator(dataSource);
    }

    @Bean
    public DatabaseGranter granter(
        @Qualifier(value = "schemaDataSource") DataSource dataSource,
        @Value("${spring.datasource.dml-user-role}") String userRole,
        @Value("${spring.jpa.properties.hibernate.default_schema}") String schema) {
      return new DatabaseGranter(userRole, schema, dataSource);
    }

    @Bean
    public DataSourceScriptDatabaseInitializer initializer(DataSource datasource) {
      return new DataSourceScriptDatabaseInitializer(datasource,
          new DatabaseInitializationSettings());
    }
  }
}

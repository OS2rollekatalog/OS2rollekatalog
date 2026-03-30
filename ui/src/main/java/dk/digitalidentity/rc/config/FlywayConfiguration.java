package dk.digitalidentity.rc.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class FlywayConfiguration {
    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Bean(initMethod = "migrate")
    public Flyway flyway(final DataSource dataSource) {
       return Flyway
             .configure()
             .configuration(Map.of("driver", driverClassName))
             .target(MigrationVersion.LATEST)
		     .validateOnMigrate(true)
             .dataSource(dataSource)
             .baselineOnMigrate(true)
		     .outOfOrder(true)
             .table("schema_version")
             .locations("classpath:/db/migration")
             .load();
    }
}

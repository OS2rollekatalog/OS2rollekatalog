package dk.digitalidentity.rc.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class FlywayTestConfiguration {

	@Bean(initMethod = "migrate")
	public Flyway flyway(DataSource dataSource) {
		Flyway flyway = new Flyway();
		flyway.setDataSource(dataSource);
		flyway.clean();
		
		flyway.setLocations("classpath:db/migration/mysql");

		return flyway;
	}
}

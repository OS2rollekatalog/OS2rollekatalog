package dk.digitalidentity.rc.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class FlywayConfiguration {

	@Value("${spring.datasource.driver-class-name}")
	private String databaseType;

	@Value("${flyway.clean:false}")
	private boolean flywayClean;

	@Bean(initMethod = "migrate")
	public Flyway flyway(DataSource dataSource) {
		Flyway flyway = new Flyway();
		flyway.setDataSource(dataSource);

		if (flywayClean) {
			flyway.clean();
		}

		if (databaseType.equals("com.microsoft.sqlserver.jdbc.SQLServerDriver")) {
			flyway.setLocations("classpath:db/migration/mssql");
		}
		else {
			flyway.setLocations("classpath:db/migration/mysql");
		}

		return flyway;
	}
}

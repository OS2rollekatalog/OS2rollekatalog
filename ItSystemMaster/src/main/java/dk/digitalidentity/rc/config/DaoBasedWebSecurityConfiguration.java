package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.security.ExtendedUserDetailsManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;

import javax.sql.DataSource;

@Configuration
public class DaoBasedWebSecurityConfiguration {

	@Bean
	public DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService) {
		DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();

		daoAuthenticationProvider.setUserDetailsService(userDetailsService);

		return daoAuthenticationProvider;
	}

	@Bean
	public UserDetailsService userDetailsService(DataSource dataSource) {
		ExtendedUserDetailsManager extendedUserDetailsManager = new ExtendedUserDetailsManager();
		
		extendedUserDetailsManager.setDataSource(dataSource);
		
		return extendedUserDetailsManager;
	}
}

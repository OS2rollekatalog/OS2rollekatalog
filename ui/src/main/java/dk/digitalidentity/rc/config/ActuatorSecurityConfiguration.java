package dk.digitalidentity.rc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@Order(99) // the default security configuration has order 100
public class ActuatorSecurityConfiguration extends WebSecurityConfigurerAdapter {

	@Value("${management.user.name:null}")
	private String username;

	@Value("${management.user.password:null}")
	private String password;

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		if (!username.equals("null") && !password.equals("null")) {
			InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
			manager.createUser(User.withUsername(username).password(password).roles("ACTUATOR", "ADMIN").build());
	
			http.authorizeRequests().antMatchers("/manage/health")
				.permitAll();
	
			http.antMatcher("/manage/**")
				.authorizeRequests()
				.anyRequest()
				.hasRole("ACTUATOR").and()
				.httpBasic().and().userDetailsService(manager);
		}
	}
}
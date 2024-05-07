package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.security.SwitchingAuthenticationProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Autowired
	private DaoAuthenticationProvider daoAuthenticationProvider;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    	http.csrf(AbstractHttpConfigurer::disable);

		http.authorizeHttpRequests(c -> c.requestMatchers("/webjars/**").permitAll());
		http.authorizeHttpRequests(c -> c.requestMatchers("/css/**").permitAll());
		http.authorizeHttpRequests(c -> c.requestMatchers("/js/**").permitAll());
		http.authorizeHttpRequests(c -> c.requestMatchers("/img/**").permitAll());
		http.authorizeHttpRequests(c -> c.requestMatchers("/api/**").permitAll());
		http.authorizeHttpRequests(c -> c.requestMatchers("/error").permitAll());
		http.authorizeHttpRequests(c -> c.requestMatchers("/manage/health").permitAll());
		http.authorizeHttpRequests(c -> c.requestMatchers("/**").hasRole("USER"));

    	http.formLogin(l -> l.loginPage("/login").permitAll());
		http.logout(l -> l.logoutRequestMatcher(new AntPathRequestMatcher("/logout")).permitAll());
		return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager() {
    	List<AuthenticationProvider> providers = new ArrayList<>();
    	providers.add(getSwitchingAuthenticationProvider());
        
    	return new ProviderManager(providers);
    }
    
    @Bean
    public SwitchingAuthenticationProvider getSwitchingAuthenticationProvider(){
    	SwitchingAuthenticationProvider provider = new SwitchingAuthenticationProvider();
    	
    	provider.setAuthenticationProvider(daoAuthenticationProvider);
		
		return provider;
    }
}

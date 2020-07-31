package dk.digitalidentity.rc.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import dk.digitalidentity.rc.security.SwitchingAuthenticationProvider;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled=true)
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

	@Autowired
	private DaoAuthenticationProvider daoAuthenticationProvider;
	
    @Override
    protected void configure(HttpSecurity http) throws Exception {
    	http.csrf().disable();
    	
		http.authorizeRequests().antMatchers("/webjars/**").permitAll();
		http.authorizeRequests().antMatchers("/css/**").permitAll();
		http.authorizeRequests().antMatchers("/js/**").permitAll();
		http.authorizeRequests().antMatchers("/img/**").permitAll();
		http.authorizeRequests().antMatchers("/api/**").permitAll();
		http.authorizeRequests().antMatchers("/error").permitAll();
		http.authorizeRequests().antMatchers("/manage/health").permitAll();
		
    	http.authorizeRequests().antMatchers("/**").hasRole("USER");

    	http
    		.formLogin()
    			.loginPage("/login")
    			.permitAll()
    		.and()
            .logout()
            	.logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
            	.permitAll();
    }

    @Bean
    @Override
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

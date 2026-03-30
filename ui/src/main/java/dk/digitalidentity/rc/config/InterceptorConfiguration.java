package dk.digitalidentity.rc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.rc.interceptor.KOMBITHookInterceptor;
import dk.digitalidentity.rc.interceptor.RoleChangeInterceptor;
import org.springframework.context.annotation.Profile;

@Configuration
public class InterceptorConfiguration {

	@Bean
	@Profile("!test")
	public RoleChangeInterceptor roleChangeInterceptor() {
		return new RoleChangeInterceptor();
	}

	@Bean
	@Profile("!test")
	public KOMBITHookInterceptor KOMBITHookInterceptor() {
		return new KOMBITHookInterceptor();
	}
}

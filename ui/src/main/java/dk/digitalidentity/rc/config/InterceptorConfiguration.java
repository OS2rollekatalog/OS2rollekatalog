package dk.digitalidentity.rc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dk.digitalidentity.rc.interceptor.KOMBITHookInterceptor;
import dk.digitalidentity.rc.interceptor.RoleChangeInterceptor;

@Configuration
public class InterceptorConfiguration {

	@Bean
	public RoleChangeInterceptor roleChangeInterceptor() {
		return new RoleChangeInterceptor();
	}

	@Bean
	public KOMBITHookInterceptor KOMBITHookInterceptor() {
		return new KOMBITHookInterceptor();
	}

}

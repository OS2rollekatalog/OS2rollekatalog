package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.interceptor.ControllerInterceptor;
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

	@Bean
	public ControllerInterceptor controllerInterceptor() {
		return new ControllerInterceptor();
	}
}

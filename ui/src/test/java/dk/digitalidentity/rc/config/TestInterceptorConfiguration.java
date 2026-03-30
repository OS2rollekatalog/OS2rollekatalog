package dk.digitalidentity.rc.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import static org.mockito.Mockito.mock;

import dk.digitalidentity.rc.interceptor.KOMBITHookInterceptor;
import dk.digitalidentity.rc.interceptor.RoleChangeInterceptor;

@TestConfiguration
@Profile("test")
public class TestInterceptorConfiguration {

	@Bean
	@Primary
	public RoleChangeInterceptor roleChangeInterceptor() {
		return mock(RoleChangeInterceptor.class);
	}

	@Bean
	@Primary
	public KOMBITHookInterceptor kombitHookInterceptor() {
		return mock(KOMBITHookInterceptor.class);
	}
}

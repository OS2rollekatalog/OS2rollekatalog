package dk.digitalidentity.rc.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import dk.digitalidentity.rc.interceptor.KOMBITHookInterceptor;
import dk.digitalidentity.rc.interceptor.RoleChangeInterceptor;

@TestConfiguration
@Profile("test")
public class TestInterceptorConfiguration {

	@Bean
	@Primary
	public RoleChangeInterceptor roleChangeInterceptor() throws Throwable {
		RoleChangeInterceptor mock = mock(RoleChangeInterceptor.class);

		// interceptCreateUser is @Around advice on UserService.save(..). A plain mock returns null and
		// never calls proceed(), so every UserService.save() would return null and skip the real persist
		// (which poisons OrganisationImporter with null users and NPEs the whole integration suite).
		// The @Before/@AfterReturning advice on this aspect - and all of KOMBITHookInterceptor - are safe
		// to leave as no-op mocks; only @Around controls the return value, so only this one must proceed.
		// The create-hook side effects it would normally fire stay suppressed, which is why we mock it.
		when(mock.interceptCreateUser(any())).thenAnswer(invocation ->
				((ProceedingJoinPoint) invocation.getArgument(0)).proceed());

		return mock;
	}

	@Bean
	@Primary
	public KOMBITHookInterceptor kombitHookInterceptor() {
		return mock(KOMBITHookInterceptor.class);
	}
}

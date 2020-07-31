package dk.digitalidentity.rc.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public class SwitchingAuthenticationProvider implements AuthenticationProvider {
	private AuthenticationProvider authenticationProvider;

	@Override
	public Authentication authenticate(Authentication authentication) {
		return authenticationProvider.authenticate(authentication);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return (UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication));
	}

	public void setAuthenticationProvider(AuthenticationProvider authenticationProvider) {
		this.authenticationProvider = authenticationProvider;
	}
}
package dk.digitalidentity.rc.service;

import dk.digitalidentity.rc.dao.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class SamlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

	@Autowired
	private UserService userService;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
										Authentication authentication) throws IOException {

		// Extract username from SAML assertion
		String username = authentication.getName();

		log.info("SAML authentication successful for user: {}", username);

		// Check if user exists in local database
		User user = userService.getByUserId(username);

		if (user == null) {
			log.warn("User {} authenticated via SAML but not found in local database", username);

			// Clear the authentication
			SecurityContextHolder.clearContext();

			// Invalidate session
			request.getSession().invalidate();

			// Redirect to error page
			response.sendRedirect("/error/user-not-found?username=" + username);
			return;
		}

		// User exists, proceed with normal login
		log.info("User {} found in database, proceeding with login", username);
		response.sendRedirect("/");
	}
}

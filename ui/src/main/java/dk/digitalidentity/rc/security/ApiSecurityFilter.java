package dk.digitalidentity.rc.security;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import dk.digitalidentity.rc.dao.model.Client;
import dk.digitalidentity.rc.dao.model.enums.AccessRole;
import dk.digitalidentity.rc.service.ClientService;

public class ApiSecurityFilter implements Filter {
	private static final Logger logger = Logger.getLogger(ApiSecurityFilter.class);
	private static final String ROLE_API = "ROLE_API_";

	private ClientService clientService;

	public ApiSecurityFilter(ClientService clientService) {
		this.clientService = clientService;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		// we are using a custom header instead of Authorization because the Authorization header plays very badly with the SAML filter
		String authHeader = request.getHeader("ApiKey");
		if (authHeader != null) {
			Client client = clientService.getClientByApiKey(authHeader);
			if (client == null) {
				unauthorized(response, "Invalid ApiKey header", authHeader);
				return;
			}

			ArrayList<GrantedAuthority> authorities = new ArrayList<>();
			switch (client.getAccessRole()) {
				case ADMINISTRATOR:
					authorities.add(new SimpleGrantedAuthority(ROLE_API + AccessRole.ORGANISATION.toString()));
					authorities.add(new SimpleGrantedAuthority(ROLE_API + AccessRole.READ_ACCESS.toString()));
					authorities.add(new SimpleGrantedAuthority(ROLE_API + AccessRole.ROLE_MANAGEMENT.toString()));
					break;
				case ORGANISATION:
					authorities.add(new SimpleGrantedAuthority(ROLE_API + AccessRole.ORGANISATION.toString()));
					break;
				case READ_ACCESS:
					authorities.add(new SimpleGrantedAuthority(ROLE_API + AccessRole.READ_ACCESS.toString()));
					break;
				case ROLE_MANAGEMENT:
					authorities.add(new SimpleGrantedAuthority(ROLE_API + AccessRole.ROLE_MANAGEMENT.toString()));
					authorities.add(new SimpleGrantedAuthority(ROLE_API + AccessRole.READ_ACCESS.toString()));
					break;
			}

			SecurityUtil.loginSystemAccount(authorities);

			filterChain.doFilter(servletRequest, servletResponse);
		} else {
			unauthorized(response, "Missing ApiKey header", authHeader);
		}
	}

	private static void unauthorized(HttpServletResponse response, String message, String authHeader) throws IOException {
		logger.warn(message + " (authHeader = " + authHeader + ")");
		response.sendError(401, message);
	}

	@Override
	public void destroy() {
		;
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
		;
	}
}

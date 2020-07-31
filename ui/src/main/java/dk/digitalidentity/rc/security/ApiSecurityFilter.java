package dk.digitalidentity.rc.security;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

public class ApiSecurityFilter implements Filter {
	private static final Logger logger = Logger.getLogger(ApiSecurityFilter.class);
	private String[] apiKeys;

	public void setApiKeys(String[] apiKeys) {
		this.apiKeys = apiKeys;
	}

	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) servletRequest;
		HttpServletResponse response = (HttpServletResponse) servletResponse;

		// we are using a custom header instead of Authorization because the Authorization header plays very badly with the SAML filter
		String authHeader = request.getHeader("ApiKey");
		if (authHeader != null) {
			boolean apiKeyMatch = false;
			
			for (String apiKey : apiKeys) {
				if (authHeader.equals(apiKey)) {
					apiKeyMatch = true;
				}
			}
			
			if (!apiKeyMatch) {
				unauthorized(response, "Invalid ApiKey header", authHeader);
				return;
			}

			SecurityUtil.loginSystemAccount();

			filterChain.doFilter(servletRequest, servletResponse);
		}
		else {
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

package dk.digitalidentity.rc.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestLogger extends OncePerRequestFilter {
	private SecurityLogger logger;

	public RequestLogger(SecurityLogger logger) {
		this.logger = logger;
	}

	private String getClientIp(HttpServletRequest request) {
		String remoteAddr = "";

		if (request != null) {
			remoteAddr = request.getHeader("X-FORWARDED-FOR");
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
		}

		return remoteAddr;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			String versionHeader = request.getHeader("ClientVersion");
			String tlsVersion = request.getHeader("TlsVersion");
			if (!StringUtils.hasLength(tlsVersion)) {
				tlsVersion = request.getHeader("x-amzn-tls-version");
			}

			String url = request.getRequestURI();
			if (StringUtils.hasLength(request.getQueryString())) {
				url += "?" + request.getQueryString();
			}

			logger.log(request, getClientIp(request), request.getMethod(), url, versionHeader, tlsVersion, response.getStatus());
		}
	}
}

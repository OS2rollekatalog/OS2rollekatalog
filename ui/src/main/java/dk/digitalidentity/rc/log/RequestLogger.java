package dk.digitalidentity.rc.log;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.filter.AbstractRequestLoggingFilter;

public class RequestLogger extends AbstractRequestLoggingFilter {
	private SecurityLogger logger;

	public RequestLogger(SecurityLogger logger) {
		this.logger = logger;
	}

	@Override
	protected void beforeRequest(HttpServletRequest request, String url) {
		logger.log(getClientIp(request), request.getMethod(), url);
	}
	
	@Override
	protected void afterRequest(HttpServletRequest request, String message) {
		; // do nothing
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
}

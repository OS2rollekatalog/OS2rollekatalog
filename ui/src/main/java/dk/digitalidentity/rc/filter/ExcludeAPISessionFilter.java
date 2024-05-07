package dk.digitalidentity.rc.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Integer.MIN_VALUE)
public class ExcludeAPISessionFilter extends OncePerRequestFilter {

	@Value("#{servletContext.contextPath}")
	private String contextPath;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {		
		if (request.getRequestURI().startsWith(contextPath + "/api/") || request.getRequestURI().startsWith(contextPath + "/manage/")) {
			request.setAttribute("org.springframework.session.web.http.SessionRepositoryFilter.FILTERED", Boolean.TRUE);
		}

		filterChain.doFilter(request, response);		
	}
}

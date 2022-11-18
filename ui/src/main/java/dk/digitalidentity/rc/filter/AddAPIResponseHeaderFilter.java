package dk.digitalidentity.rc.filter;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AddAPIResponseHeaderFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {		
		if (request.getRequestURI().startsWith("/api/")) {
			response.setHeader("Content-type", "application/json;charset=utf-8");
		}

		filterChain.doFilter(request, response);		
	}
}

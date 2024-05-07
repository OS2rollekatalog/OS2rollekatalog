package dk.digitalidentity.rc.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;

public class InternetExplorerHttpsFontFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (response instanceof HttpServletResponse) {
			HttpServletResponseWrapper wrapper = new HttpServletResponseWrapper((HttpServletResponse) response) {

				// do not let anyone else overwrite these values - otherwise our fonts will not be cached correctly on IE
				
				@Override
				public void setHeader(String name, String value) {
					if (name.equalsIgnoreCase("expires")) {
						value = "-1";
					}
					else if (name.equalsIgnoreCase("cache-control")) {
						value = "public";
					}
					else if (name.equalsIgnoreCase("pragma")) {
						value = "cache";
					}

					super.setHeader(name, value);
				}
				
				@Override
				public void addHeader(String name, String value) {
					if (name.equalsIgnoreCase("expires") || name.equalsIgnoreCase("cache-control") || name.equalsIgnoreCase("pragma")) {
						return;
					}

					super.addHeader(name, value);
				}
			};

			wrapper.setHeader("Expires", "-1");
			wrapper.setHeader("Cache-control", "public");
			wrapper.setHeader("Pragma", "cache");
			
			chain.doFilter(request, wrapper);
		}
		else {
			chain.doFilter(request, response);
		}
	}

	@Override
	public void init(FilterConfig config) throws ServletException {
		
	}

	@Override
	public void destroy() {
		
	}
}

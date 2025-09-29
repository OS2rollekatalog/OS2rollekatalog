package dk.digitalidentity.rc.filter;
import dk.digitalidentity.rc.security.SecurityUtil;
import dk.digitalidentity.samlmodule.model.TokenUser;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

public class SwaggerSecurityFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if (isLoggedIn()) {
            filterChain.doFilter(request, response);
        }
        else {
            // Redirect to SAML login page
            response.sendRedirect("/");
        }
    }

    // The securityUtil method is private, we assume it is for good reason, so we make our own
    private static boolean isLoggedIn() {
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getDetails() != null
                && SecurityContextHolder.getContext().getAuthentication().getDetails() instanceof TokenUser) {
            return true;
        }

        return false;
    }
}

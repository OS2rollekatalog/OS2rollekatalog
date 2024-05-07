package dk.digitalidentity.rc.config;

import dk.digitalidentity.rc.log.RequestLogger;
import dk.digitalidentity.rc.log.SecurityLogger;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

@Configuration
public class RequestLoggerConfiguration {

	@Bean
	public RequestLogger logFilter(SecurityLogger logger) {
		return new RequestLogger(logger);
	}

	@Bean
	public FilterRegistrationBean<RequestLogger> logFilterRegistration(RequestLogger filter) {
		List<String> urlPatterns = new ArrayList<>();
		urlPatterns.add("/api/*");

		FilterRegistrationBean<RequestLogger> registration = new FilterRegistrationBean<>();
		registration.setFilter(filter);
		registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
		registration.setUrlPatterns(urlPatterns);
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);

		return registration;
	}
}
